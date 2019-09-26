* Install maven

``` shell
#Install maven (if you don't already have)
export MAVEN_VERSION=3.6.2
mkdir -p /usr/local/maven
wget "http://mirrors.ocf.berkeley.edu/apache/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -O - | tar -xz -C /usr/local/maven
ln -sf /usr/local/maven/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn

#Install git (if you don't already have)
yum install -y git

```

* Build 
``` shell
#clone repository
cd ~ && git clone https://github.com/npopa/hbase-tools

#build repository
export JAVA_HOME=/usr/java/default
cd ~/hbase-tools && git pull && mvn clean package


```