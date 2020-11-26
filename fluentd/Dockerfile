FROM fluent/fluentd
RUN ["gem", "install", "fluent-plugin-logzio", "--no-rdoc", "--no-ri"]
COPY td-agent.conf /fluentd/etc/fluent.conf
EXPOSE 24224
ENTRYPOINT ["fluentd", "-c", "/fluentd/etc/fluent.conf"]