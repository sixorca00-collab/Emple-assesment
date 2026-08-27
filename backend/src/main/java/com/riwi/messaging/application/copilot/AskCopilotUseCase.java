package com.riwi.messaging.application.copilot;

import com.riwi.messaging.domain.exception.InvalidInputException;
import com.riwi.messaging.domain.exception.ResourceNotFoundException;
import com.riwi.messaging.domain.model.ActorProfile;
import com.riwi.messaging.domain.model.CopilotAnswer;
import com.riwi.messaging.domain.model.CopilotCitation;
import com.riwi.messaging.domain.model.CopilotQueryRecord;
import com.riwi.messaging.domain.model.CopilotStatus;
import com.riwi.messaging.domain.model.CopilotTokenUsage;
import com.riwi.messaging.domain.model.RetrievedMessage;
import com.riwi.messaging.domain.port.ChatPort;
import com.riwi.messaging.domain.port.CopilotContextRepository;
import com.riwi.messaging.domain.port.CopilotQueryRepository;
import com.riwi.messaging.domain.port.EmbeddingPort;
import com.riwi.messaging.domain.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// caso de uso del copiloto RAG: recupera contexto propio del actor, responde con citas o se niega
@Service
public class AskCopilotUseCase {

    private static final int MAX_QUESTION_LENGTH = 4000;
    private static final int SNIPPET_LENGTH = 180;

    // marcador de "sin proveedor" para las negativas: no hubo llamada al modelo
    private static final String NO_MODEL = "none";

    // negativas honestas segun el motivo
    private static final String REFUSAL_NO_CONTEXT =
            "No tengo informacion suficiente en tus conversaciones para responder eso.";
    private static final String REFUSAL_PERMISSION =
            "No puedo responder: esa informacion pertenece a canales a los que no tienes acceso.";

    private final UserRepository users;
    private final EmbeddingPort embeddings;
    private final CopilotContextRepository context;
    private final ChatPort chat;
    private final CopilotQueryRepository queries;
    private final CopilotPromptBuilder prompts;
    private final CopilotSettings settings;

    public AskCopilotUseCase(UserRepository users,
                             EmbeddingPort embeddings,
                             CopilotContextRepository context,
                             ChatPort chat,
                             CopilotQueryRepository queries,
                             CopilotPromptBuilder prompts,
                             CopilotSettings settings) {
        this.users = users;
        this.embeddings = embeddings;
        this.context = context;
        this.chat = chat;
        this.queries = queries;
        this.prompts = prompts;
        this.settings = settings;
    }

    // nota: la llamada al proveedor va dentro de la transaccion por simplicidad del assessment;
    // en produccion la inferencia se resolveria fuera de la transaccion o en una cola
    @Transactional
    public CopilotAnswer execute(UUID actorId, AskCopilotCommand command) {
        // validamos la pregunta: vacia o excesiva no es una consulta valida
        String question = command.question() == null ? "" : command.question().strip();
        if (question.isEmpty()) {
            throw new InvalidInputException("question must not be blank");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new InvalidInputException("question is too long");
        }

        // contexto del usuario construido en el servidor desde la BD, nunca del body
        ActorProfile profile = users.findProfileById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        // embebemos la pregunta con el puerto de embeddings
        float[] questionEmbedding = embeddings.embed(question);

        // recuperamos el contexto con los permisos aplicados EN SQL (Consulta 3) + RLS
        List<RetrievedMessage> retrieved = context.retrieveForActor(
                actorId, questionEmbedding, settings.topK(), settings.minSimilarity());

        if (retrieved.isEmpty()) {
            // distinguimos "no hay nada" de "hay algo, pero no para ti"
            boolean existsElsewhere = context.contextExistsAnywhere(questionEmbedding, settings.minSimilarity());
            CopilotStatus status = existsElsewhere
                    ? CopilotStatus.REFUSED_PERMISSION
                    : CopilotStatus.REFUSED_NO_CONTEXT;
            String refusal = existsElsewhere ? REFUSAL_PERMISSION : REFUSAL_NO_CONTEXT;

            // persistimos la negativa igual que una respuesta, sin citas
            queries.persist(new CopilotQueryRecord(
                    actorId, question, refusal, NO_MODEL,
                    status, settings.systemPromptVersion(), CopilotTokenUsage.none(), List.of()));

            return new CopilotAnswer(refusal, status, List.of(), CopilotTokenUsage.none());
        }

        // armamos el prompt: system versionado + contexto no confiable delimitado
        String systemPrompt = prompts.systemPrompt(profile.displayName(), profile.jobTitle());
        String userPrompt = prompts.userPrompt(retrieved, question);

        // llamamos al puerto de chat y capturamos el consumo real de tokens
        ChatPort.ChatResult result = chat.complete(systemPrompt, userPrompt);
        CopilotTokenUsage usage = CopilotTokenUsage.of(result.promptTokens(), result.completionTokens());

        // las citas son los mensajes efectivamente recuperados, en orden de relevancia
        List<CopilotCitation> citations = toCitations(retrieved);

        // persistimos consulta + citas en la misma transaccion (actor RLS ya fijado por el aspecto)
        queries.persist(new CopilotQueryRecord(
                actorId, question, result.content(), result.model(),
                CopilotStatus.ANSWERED, settings.systemPromptVersion(), usage, citations));

        return new CopilotAnswer(result.content(), CopilotStatus.ANSWERED, citations, usage);
    }

    private List<CopilotCitation> toCitations(List<RetrievedMessage> retrieved) {
        return java.util.stream.IntStream.range(0, retrieved.size())
                .mapToObj(i -> {
                    RetrievedMessage m = retrieved.get(i);
                    return new CopilotCitation(m.messageId(), m.channelId(), snippet(m.body()), i + 1);
                })
                .toList();
    }

    private static String snippet(String body) {
        if (body.length() <= SNIPPET_LENGTH) {
            return body;
        }
        return body.substring(0, SNIPPET_LENGTH - 3) + "...";
    }
}
