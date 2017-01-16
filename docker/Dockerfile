FROM <<insert image name here that includes JDK7>>

EXPOSE 8080

# add CD scripts (separate from prev step to prevent install-java to trigger too much)
COPY cd_*.sh /opt/
RUN chmod +x /opt/cd_*.sh

# Add app
COPY be*.jar /opt/
CMD ["sh", "/opt/cd_run.sh"]

