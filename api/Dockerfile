FROM openjdk:8

ENV IDC zeus
ENV MODULE bella-openapi
ENV ENVTYPE test
ENV DEBUGPORT 9008
ENV JMXPORT 9009
ENV MATRIX_CODE_DIR /opt/bella-openapi/htdocs
ENV MATRIX_APPLOGS_DIR /opt/bella-openapi/applogs
ENV MATRIX_ACCESSLOGS_DIR /opt/bella-openapi/logs
ENV MATRIX_LOGS_DIR /opt/bella-openapi/logs
ENV MATRIX_CACHE_DIR /opt/bella-openapi/cache
ENV MATRIX_PRIVDATA_DIR /opt/bella-openapi/privdata

COPY release/ /opt/bella-openapi/htdocs/
RUN chmod +x /opt/bella-openapi/htdocs/bin/*.sh

EXPOSE 8080 9008 9009
WORKDIR /opt/bella-openapi/htdocs
VOLUME ["/opt/bella-openapi/applogs", "/opt/bella-openapi/logs", "/opt/bella-openapi/cache", "/opt/bella-openapi/privdata", "/opt/bella-openapi/config-cache"]
CMD ["/bin/bash", "-x", "/opt/bella-openapi/htdocs/bin/run.sh"]
