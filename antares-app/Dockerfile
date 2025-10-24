# --- STAGE 1: Build Angular ---
FROM node:25-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build -- --configuration production

# --- STAGE 2: nginx ---
FROM nginx:stable-alpine

# Clean default conf
RUN rm -f /etc/nginx/conf.d/default.conf

# Copy our nginx.conf
COPY nginx.conf /etc/nginx/nginx.conf

# Copy built app
COPY --from=builder /app/dist/antares-app/browser /usr/share/nginx/html

# Ensure nginx can read the files
RUN chmod -R 755 /usr/share/nginx/html && chown -R nginx:nginx /usr/share/nginx/html

# Expose HTTP and HTTPS ports (compose maps them)
EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
