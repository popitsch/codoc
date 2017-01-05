# clone official java7 docker image
FROM java:7
# copy codoc executable
COPY bin/codoc-0.0.2.jar /usr/src/
# define entrypoint type as "exec" 
ENTRYPOINT ["java", "-jar", "/usr/src/codoc-0.0.2.jar"]
# define default behaviour (if no params were passed)
CMD ["java", "-jar", "/usr/src/codoc-0.0.2.jar"]
