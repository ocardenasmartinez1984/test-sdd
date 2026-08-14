# Stage 1: Build Angular app
FROM node:20-alpine AS builder
WORKDIR /app
COPY pos-frontend/package.json pos-frontend/package-lock.json* ./
RUN npm install
COPY pos-frontend/ .
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine
COPY --from=builder /app/dist/pos-frontend/browser /usr/share/nginx/html
COPY pos-frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
