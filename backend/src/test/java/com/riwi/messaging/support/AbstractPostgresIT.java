package com.riwi.messaging.support;

import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

// base de los tests de integracion: Postgres real con pgvector, migraciones + seed cargados una sola vez
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractPostgresIT {

    // superusuario bootstrap del contenedor (NO se llama riwi_app): corre migraciones y seed
    private static final String BOOTSTRAP_USER = "riwi_root";
    private static final String BOOTSTRAP_PASSWORD = "root";

    // rol de aplicacion creado por V2__rls.sql, con el que se conecta el backend bajo prueba
    private static final String APP_USER = "riwi_app";
    private static final String APP_PASSWORD = "riwi_app";

    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        // docker-java negocia una API demasiado vieja por defecto; fijamos una version que todo daemon moderno acepta
        if (System.getProperty("api.version") == null && System.getenv("DOCKER_API_VERSION") == null) {
            System.setProperty("api.version", "1.41");
        }

        POSTGRES = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("bd_riwi_test")
                .withUsername(BOOTSTRAP_USER)
                .withPassword(BOOTSTRAP_PASSWORD);
        POSTGRES.start();
        migrate();
        loadSeed();
    }

    // Flyway corre como el superusuario bootstrap (unica forma de crear el rol riwi_app)
    private static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), BOOTSTRAP_USER, BOOTSTRAP_PASSWORD)
                .locations("filesystem:../db/migrations")
                .load()
                .migrate();
    }

    // reutilizamos la funcion rw_load_seed de db/seed_loader.sql y el corpus db/seed.json
    private static void loadSeed() {
        try {
            String loader = Files.readString(Path.of("../db/seed_loader.sql"));
            int start = loader.indexOf("CREATE OR REPLACE FUNCTION rw_load_seed");
            int end = loader.indexOf("$$;", start) + "$$;".length();
            String createLoaderFn = loader.substring(start, end);
            String seedJson = Files.readString(Path.of("../db/seed.json"));

            try (Connection conn = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), BOOTSTRAP_USER, BOOTSTRAP_PASSWORD)) {
                // creamos la funcion de carga
                try (Statement statement = conn.createStatement()) {
                    statement.execute(createLoaderFn);
                }
                // ejecutamos la carga pasando el corpus como parametro (jsonb)
                try (PreparedStatement ps = conn.prepareStatement("SELECT rw_load_seed(?::jsonb, true)")) {
                    ps.setString(1, seedJson);
                    ps.execute();
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to load seed corpus", e);
        }
    }

    // ejecuta trabajo como el superusuario bootstrap (bypassa RLS): util para preparar embeddings en tests
    protected static void runAsBootstrap(BootstrapWork work) {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), BOOTSTRAP_USER, BOOTSTRAP_PASSWORD)) {
            work.run(conn);
        } catch (Exception e) {
            throw new IllegalStateException("bootstrap work failed", e);
        }
    }

    @FunctionalInterface
    protected interface BootstrapWork {
        void run(Connection connection) throws Exception;
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // el backend bajo prueba se conecta como riwi_app => queda sujeto a RLS
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("riwi.jwt.secret", () -> "integration-test-secret-integration-test-secret");
        registry.add("riwi.jwt.access-expiration-minutes", () -> "15");
        registry.add("riwi.jwt.refresh-expiration-days", () -> "7");
    }
}
