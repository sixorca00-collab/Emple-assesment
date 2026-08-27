# --- build stage: compila la SPA de Angular ---
FROM node:20-alpine AS build
WORKDIR /app
# cache de dependencias: primero los manifiestos
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# URL del backend: se resuelve en build-time reemplazando la constante del bundle
# (api.config.ts es build-time; ver README seccion "Frontend / API_BASE_URL")
ARG API_BASE_URL=http://localhost:8080
RUN sed -i "s|http://localhost:8080|${API_BASE_URL}|g" src/app/shared/config/api.config.ts
RUN npm run build

# --- runtime stage: nginx sirviendo el bundle estatico ---
FROM nginx:1.27-alpine AS runtime
# config con fallback SPA (las rutas de Angular las resuelve el cliente)
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
