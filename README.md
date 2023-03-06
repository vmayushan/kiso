
<br />
<div align="center">
  <h3 align="center">Kotlin Isolated Code Sandbox</h3>
</div>


<!-- ABOUT THE PROJECT -->
## About The Project

An isolated sandbox environment which allows execution of untrusted code snippets posted by user.

Currently, C# and Python scripts are supported.

![image](https://user-images.githubusercontent.com/9516149/222988729-12c2b6d9-adff-4f92-b2f5-6ff0d91ed4ed.png)


<!-- GETTING STARTED -->
## Getting Started

### Prerequisites

To start or execute integration tests project locally you will need mongo and redis
* mongo
  ```sh
  docker run -d --name mongo -p 27017:27017 mongo:latest
  ```
* redis
  ```sh
  docker run -d --name redis-stack -p 6379:6379 -p 8001:8001 redis/redis-stack:latest
  ```
---
## Built With

* Kotlin/JVM
* Redis Streams
* MongoDb
* Docker

## Project modules overview
### API
#### POST /job/
**Request**:

| Field      | Type                                                           |
|------------|----------------------------------------------------------------|
| language   | String (CSharp or Python)                                      |
| sourceCode | String                                                         |
| packages   | (Optional) Array of { name: String, version: String? } objects |

Responds with **201 Created** with ID of just started execution job in the response body
#### GET /job/{job_id}/
Returns a current execution job output 

#### WEBSOCKET /ws/job/{job_id}/
Realtime execution job progress

---

### Worker
It's responsibility is to take execution jobs from an execution queue and to run it in isolated environment.

#### Important features
* easy to extend to other target programming languages
* concurrent execution of multiple scripts with desired level of parallelism
* graceful shutdown: after SIGINT signal worker stops receiving new jobs and finishes currently running jobs
* ability to recover after permanent failures: all running jobs are taken by other workers
* supports idle container precreation to improve execution preparation time
* security by using docker isolation features to set limits on CPU, RAM, Disk, STDOUT, limiting internet connection and using readonly container file system

---

### Reducer
Responsible for data replication from Redis in memory database to permanent MongoDB storage

---

### Core 
Library with shared data access layer used by Worker and Reducer

---

## Usage examples
### Create execution job (serialization using Newtonsoft.Json)

```
POST http://localhost:8080/job
Content-Type: application/json

{
"language": "csharp",
"sourceCode": "using Newtonsoft.Json;\nConsole.WriteLine(JsonConvert.SerializeObject(new {a=1,b=2,c=3}));",
"packages": [{"name": "Newtonsoft.Json"}]
}
 ```
Response:
 ```
HTTP/1.1 201 Created
Content-Length: 45
Content-Type: application/json
Connection: keep-alive

{
"id": "2b950486-5a85-4117-87f2-e32018f69071"
}
 ```

### Get execution status
```
GET http://localhost:8080/job/2b950486-5a85-4117-87f2-e32018f69071
```
Response
```
HTTP/1.1 200 OK
Content-Length: 1905
Content-Type: text/plain; charset=UTF-8
Connection: keep-alive

Execution started
Step[name=Setup] started
Determining projects to restore...
Writing /tmp/tmpFKMjCm.tmp
info : X.509 certificate chain validation will use the fallback certificate bundle at '/usr/share/dotnet/sdk/6.0.406/trustedroots/codesignctl.pem'.
info : Adding PackageReference for package 'Newtonsoft.Json' into project '/sandbox-tmpfs/app.csproj'.
info :   GET https://api.nuget.org/v3/registration5-gz-semver2/newtonsoft.json/index.json
info :   OK https://api.nuget.org/v3/registration5-gz-semver2/newtonsoft.json/index.json 205ms
info : Restoring packages for /sandbox-tmpfs/app.csproj...
info :   GET https://api.nuget.org/v3-flatcontainer/newtonsoft.json/index.json
info :   OK https://api.nuget.org/v3-flatcontainer/newtonsoft.json/index.json 200ms
info :   GET https://api.nuget.org/v3-flatcontainer/newtonsoft.json/13.0.2/newtonsoft.json.13.0.2.nupkg
info :   OK https://api.nuget.org/v3-flatcontainer/newtonsoft.json/13.0.2/newtonsoft.json.13.0.2.nupkg 178ms
info : Installed Newtonsoft.Json 13.0.2 from https://api.nuget.org/v3/index.json with content hash R2pZ3B0UjeyHShm9vG+Tu0EBb2lC8b0dFzV9gVn50ofHXh9Smjk6kTn7A/FdAsC8B5cKib1OnGYOXxRBz5XQDg==.
info : Package 'Newtonsoft.Json' is compatible with all the specified frameworks in project '/sandbox-tmpfs/app.csproj'.
info : PackageReference for package 'Newtonsoft.Json' version '13.0.2' added to file '/sandbox-tmpfs/app.csproj'.
info : Generating MSBuild file /sandbox-tmpfs/obj/app.csproj.nuget.g.props.
info : Writing assets file to disk. Path: /sandbox-tmpfs/obj/project.assets.json
log  : Restored /sandbox-tmpfs/app.csproj (in 2.89 sec).
Step[name=Setup] completed in 5123 ms
Step[name=Compile] started

Build succeeded.
0 Warning(s)
0 Error(s)

Time Elapsed 00:00:01.64
Step[name=Compile] completed in 1947 ms
Step[name=Execute] started
{"a":1,"b":2,"c":3}
Step[name=Execute] completed in 169 ms
Execution completed
```