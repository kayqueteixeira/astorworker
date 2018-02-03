# AstorWorker
The second half of the [Astor4Android](https://github.com/kayquesousa/astor4android).

Contacts:  
Kayque de S. Teixeira - kayque23@gmail.com  
Celso G Camilo-Junior - celsocamilo@gmail.com  

## Compilation

1. Install Android SDK, JDK 1.8 and Maven.

2. Set the environment variables ANDROID_HOME and JAVA_HOME.  
	
	Example:  
	`export ANDROID_HOME=/home/kayquesousa/Android/Sdk`  
	`export JAVA_HOME=/home/kayquesousa/jdk1.8.0_131`  

3. Clone the AstorWorker repository and compile it using Maven:
	
	`git clone https://github.com/kayquesousa/astorworker.git`  
	`cd astorworker`  
	`mvn clean compile`  

## Setup a headless Android emulator

### Create an AVD

1. Go to the tools/bin folder:

	`cd $ANDROID_HOME/tools/bin`  

2. Get the system image list:

	`./sdkmanager --list --verbose | grep "system-image"`  

3. Select a system image version of your preference from the list.

	Example of a system image:  system-images;android-26;google_apis;x86_64 

4. Download the system image:

	`./sdkmanager "system-images;android-26;google_apis;x86_64"`   

5. Create the AVD:

	`./avdmanager create avd --force -n 'AVDNAME' -k 'system-images;android-26;google_apis;x86_64'`

	Where AVDNAME is a name of your choice.

### Execute the emulator

1. To run the headless emulator, run these commands:

	`export QEMU_AUDIO_DRV=none`  
	`cd $ANDROID_HOME/tools`  
	`sudo -b ./emulator -avd AVDNAME -no-skin -no-window -no-boot-anim`  

### Shutdown the emulator

1. To shutdown the headless emulator, run this command:

	`sudo printf 'auth %s\nkill\n' $(sudo cat ~/.emulator_console_auth_token) | netcat localhost 5554`


## Execution 

AstorWorker has the following command line arguments:

| Argument | Description |
| --- | --- |
| hostip | IP of the host machine that is running Astor4Android. | 
| hostport | Port used to locate the Astor4Android instance at the host. |
| workerip | IP of the machine the AstorWorker is going to execute. |
| workerport | Port used to locate the AstorWorker instance at it's machine. |
| androidsdk | Location of the Android SDK folder. Usually this argument is set to $ANDROID_HOME. |

To run AstorWorker, run this command (replacing `<arguments>` with the actual arguments):
   `mvn exec:java -Dexec.mainClass=br.ufg.inf.astorworker.main.AstorWorker -Dexec.args="<arguments>"`  

   Example:  

			mvn exec:java -Dexec.mainClass=br.ufg.inf.astorworker.main.AstorWorker -Dexec.args="-androidsdk $ANDROID_HOME -hostip 192.155.52.54 -hostport 6665 -workerip 192.155.2.52 -workerport 6666"

There's a script called "run" inside the main folder that can be used as a template for a script that starts an instance of AstorWorker.
  





