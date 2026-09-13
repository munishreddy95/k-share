FROM node:20-alpine

ENV NODE_ENV=production \
    PUBLIC_HOST=0.0.0.0 \
    PUBLIC_PORT=4444 \
    AGENT_PATH=/agent

WORKDIR /app

RUN addgroup -S flux && adduser -S flux -G flux

COPY --chown=flux:flux package.json server.js ./

USER flux

EXPOSE 4444

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "const http=require('http');const req=http.get({host:'127.0.0.1',port:process.env.PUBLIC_PORT||4444,path:'/healthz',timeout:3000},res=>process.exit(res.statusCode===200?0:1));req.on('error',()=>process.exit(1));req.on('timeout',()=>{req.destroy();process.exit(1);});"

CMD ["node", "server.js"]
