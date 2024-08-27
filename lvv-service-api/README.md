# Quick Start
## Building
From the package root:
  ```mvn clean install```

### Troubleshooting
Make sure your development environment is set up properly. Including, making sure that the maven settings.xml file has the correct content. See the following links for details:
https://confluence.oci.oraclecorp.com/display/PGI/Dev+Environment+and+Tools+Setup
https://confluence.oci.oraclecorp.com/display/IODOCS/Artifactory+-+Maven+Repositories


## Running
Running:`./run.sh`
With a different config:`./run.sh config/dev.conf`
With debugging: `./run.sh --debug`
With a debug port: `./run.sh --debug=9090`
With lots of options: `./run.sh config/dev.conf --debug=9090`

# Endpoints
## Standard port (21000):
*` /ui` - The API explorer
* `/spec/api.json` - The API spec in JSON format

## Admin port (21001):
* `/` - A list of operational tools
* `/threads` - A list of threads
* `/metrics?pretty=true` - Metrics
* `/ping` - A ping command
* `/healthcheck?pretty=true` - Runs and prints out the health checks

# Features
* Configured to build with Maven
* Guice enabled
* Lombok enabled
* UI API explorer at /ui
* API spec exposed in JSON format at /spec/api.json
* Generated abstract resource classes based on the API spec

# Operational Readiness Checklist

Before moving your application to testing and ultimately production, address these checklist items

- [ ] Update default healthcheck (TODO: Documentation link)


# How to test locally with Kiev
Follow the following steps for testing the service locally using Kiev-in-a-box

## Steps for local setup

1. Setup using a tunnel 
  
   Setup a tunnel to your overlay host from your local machine (we recommend using a beta/integ env host)

   Why?

   This is what will allow you to access instance principal certs locally and test auth functionality.
   You could also cat out the certs and use the files but this is much easier and will work over time
   as the certs rotate every 2 hours.

   How?

   The instance principal is unavailable locally but available on every instance in the cloud and can be obtained from the metadata service of the host. (Ref:  https://docs.cloud.oracle.com/en-us/iaas/Content/Compute/Tasks/gettingmetadata.htm)
   With the below ssh tunnel, the requests to localhost:8000 are forwarded to the metadata service (169.254.169.254:80):

   `ssh -L 8000:169.254.169.254:80 your_user@<ip_of_your_integ_env_host>`

   More about ssh tunneling:  https://www.ssh.com/ssh/tunneling/example


2. Setup Kiev in a box:

   **Prerequisite:** Colima, Docker and Wget Setup Kiev-in-a-box by following: https://bitbucket.oci.oraclecorp.com/projects/KIEV/repos/kiab-cli/browse

   During the *Install Kiev* step execute the following:

   `kiab kiev create -u lvvproject -p lvvproject123456`

   Verify the kiev database is working by connecting through kqt as mentioned in the Kiev-in-a-box README
   1. Perform the following command to build the service and the clients:

      `mvn clean install`
   2. Execute _./run.sh_ inside lvv-service-api

   3. You should be able to hit the following 3 endpoints from swagger UI or Postman or Curl:
      1. Create projects using PUT by providing the projectId and json body:
      
         ```
         curl --header "Content-Type: application/json" --request PUT --data '{"project": { "vendorName": "vendor2", "building": "112", "block": "029", "type": "cabling"}}' http://localhost:21000/lvv/projects/DO116
         ```
      2. Get project using GET by providing the project Id:
      
         ```
         curl --header "Content-Type: application/json" http://localhost:21000/lvv/projects/DO116
         ```
      3. Get project list for specific vendor using GET by providing the vendorName:

         ```
         curl --header "Content-Type: application/json" http://localhost:21000/lvv/projects?vendorName=vendor2
         ```
   
      
# How to test Jira related functions on local desktop
1. Follow https://dyn.slack.com/archives/GAJ2G1U56/p1709777583665419 to setup OSSH. Here is the full version of the guideline:  
   https://confluence.oci.oraclecorp.com/display/SS/OSSH+%28OCI+SSH%29+User+Guide
2. Run this command to create an one-time JIT (Just in Time) password which lasts for 10 hours.
```agsl
ssh operator-access-token.svc.ad1.us-ashburn-1 'generate --mode=password'
```
Here is the full guideline of JIT in case the above command is out of date https://confluence.oci.oraclecorp.com/display/SS/JIT+%28Just+in+Time%29+Password+Service+User+Guide
3. Put the generated password in src/main/resources/jira-sd-test-password (without the last %)
4. Put your OCI email in src/main/resources/jira-sd-test-username, for example a.b@oracle.com
5. Now you can run the service, should be able to call to Jira
6. Remember don't commit these two files to git