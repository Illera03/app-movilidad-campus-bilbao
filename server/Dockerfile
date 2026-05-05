FROM node:20-alpine

WORKDIR /app

# Instalar dependencias de compilación para better-sqlite3
RUN apk add --no-cache python3 make g++

COPY package.json ./
RUN npm install --production

COPY . .

# Crear directorio para la BD persistente
RUN mkdir -p /app/data

EXPOSE 3000

CMD ["node", "index.js"]
