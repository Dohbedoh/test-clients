FROM openjdk:11

ARG user=test
ARG group=test
ARG uid=1000
ARG gid=1000

RUN groupadd -g "${gid}" "${group}" \
  && useradd -l -c "Test user" -d /home/${user} -u "${uid}" -g "${gid}" -m "${user}"

ENV LANG C.UTF-8

ADD --chown="${user}":"${group}" target/test-clients-1.0-SNAPSHOT-jar-with-dependencies.jar /usr/share/test-clients/test-clients.jar
RUN chmod 0644 /usr/share/test-clients/test-clients.jar
COPY ./entrypoint /usr/local/bin/entrypoint
RUN chmod +x /usr/local/bin/entrypoint

USER "${user}"

WORKDIR /home/"${user}"

ENTRYPOINT ["/usr/local/bin/entrypoint"]