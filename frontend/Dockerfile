# ---------------------------------------------------------------- build
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---------------------------------------------------------------- pubblicazione (nginx)
FROM nginx:1.30-alpine
# indirizzo pubblico dell'applicazione (nome host e porta, come lo scrivono gli utenti nel browser)
ENV ORDITOGRAM_INDIRIZZO=localhost:8081
# solo questa variabile viene sostituita: le variabili di nginx ($uri, $scheme...) restano come sono
ENV NGINX_ENVSUBST_FILTER=^ORDITOGRAM_
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost/ >/dev/null || exit 1
