# Build stage
FROM node:24.13.1-alpine3.23 as build-stage

WORKDIR /app

COPY package*.json ./

# 彻底清除代理环境变量，并强制安装 yarn
RUN unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY && \
    npm config delete proxy && \
    npm config delete https-proxy && \
    npm install -g yarn --force && \
    yarn config set registry https://registry.npmmirror.com && \
    yarn install && \
    yarn add -O @rollup/rollup-linux-x64-musl

COPY . .

RUN yarn build

# Production stage
FROM nginx:stable-alpine as production-stage

COPY --from=build-stage /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
