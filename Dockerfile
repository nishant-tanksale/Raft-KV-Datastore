# Use Maven to build the application
FROM maven:3.6.0-jdk-11-slim AS build

# Set the working directory
WORKDIR /home/app

# Copy the Maven POM file
COPY pom.xml .

# Download and cache the dependencies
RUN mvn dependency:go-offline

# Copy the source code
COPY src /home/app/src

# Package the application
RUN mvn clean package

#
# Package stage
#
FROM openjdk:11-jre-slim

# Copy the built JAR from the build stage
COPY --from=build /home/app/target/ratis-rocksdb-kvstore-1.0-SNAPSHOT.jar /usr/local/lib/demo.jar

# Define the entry point
ENTRYPOINT ["java", "-jar", "/usr/local/lib/demo.jar"]
