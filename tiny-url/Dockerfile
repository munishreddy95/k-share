FROM node:20-alpine

ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=4100 \
    DATA_FILE=/data/links.json

WORKDIR /app

RUN addgroup -S flux && adduser -S flux -G flux

COPY --chown=flux:flux package.json server.js ./

RUN mkdir -p /data && chown -R flux:flux /data

USER flux

EXPOSE 4100

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "const http=require('http');const req=http.get({host:'127.0.0.1',port:process.env.PORT||4100,path:'/healthz',timeout:3000},res=>process.exit(res.statusCode===200?0:1));req.on('error',()=>process.exit(1));req.on('timeout',()=>{req.destroy();process.exit(1);});"

CMD ["node", "server.js"]
