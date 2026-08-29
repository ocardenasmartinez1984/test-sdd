# syntax=docker/dockerfile:1.7
# Stage 1: Build Angular app
FROM node:20-alpine AS builder
WORKDIR /app

# Install dependencies first (better layer caching). Uses a shared npm cache
# mount so packages are downloaded once and reused across all frontend builds.
COPY package.json package-lock.json* ./
RUN --mount=type=cache,target=/root/.npm,id=npm-cache,sharing=locked \
    npm ci --prefer-offline --no-audit --no-fund

# Copy source and build
COPY . .
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine
COPY --from=builder /app/dist/users-mantenedor/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
