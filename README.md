# AstorWorker
The second half of the [Astor4Android](https://github.com/kayquesousa/astor4android).

## Compilation

1. Install Android SDK, JDK 1.8 and Maven.

2. Set the environment variables ANDROID_HOME and JAVA_HOME.  
	
	Example:  
	`export ANDROID_HOME=/home/kayquesousa/Android/Sdk`  
	`export JAVA_HOME=/home/kayquesousa/jdk1.8.0_131`  

4. Clone the AstorWorker repository and compile it using Maven:
	
	`git clone https://github.com/kayquesousa/astorworker.git`  
	`cd astorworker`  
	`mvn clean compile`  

## Setup a headless Android emulator

### Create an AVD

1. Go to the tools location inside the Android SDK folder:

	`cd $ANDROID_HOME/tools`  

2. Get the SDK list:

	`./android list sdk --extended --no-ui --all`  

3. Select a system image version of your preference from the list.

	Some elements from the list follow this pattern:  

		id: NUMBER or "SYSTEM_IMAGE"  
			Type: SystemImage  
			Desc: STRING  
				  Revision NUMBER  
				  Requires SDK Platform Android API NUMBER  

	The information needed is SYSTEM_IMAGE.  

4. Download the System image:

	`sudo ./android update sdk -a --no-ui --filter SYSTEM_IMAGE`  

5. Get the target list:  

	`./android list targets`  

6. Select a target from the list

	Elements from the list follow this pattern:  

			id: NUMBER or "TARGET"  
	 		Name: STRING  
	 		Type: Platform  
			API level: NUMBER  
	 		Revision: NUMBER  
	 		Skins: STRING, STRING, ...  
			Tag/ABIs : ABI, ABI, ...  

	The information needed is TARGET and one of the ABIs.  

7. Create the AVD:

	`./android create avd --force -n AVDNAME -t TARGET --abi ABI`

	Where AVDNAME is a name of your choice.

### Execute the emulator

1. To run the headless emulator, execute this commands:

	`export QEMU_AUDIO_DRV=none`  
	`sudo -b ./emulator -avd AVDNAME -no-skin -no-window -no-boot-anim`  

### Shutdown the emulator

1. To shutdown the headless emulator, execute this command:

	`sudo printf 'auth %s\n kill\n' $(sudo cat ~/.emulator_console_auth_token) | netcat localhost 5554`


## Execution 

AstorWorker have the following command line arguments:

| Argument | Description |
| --- | --- |
| hostip | IP of the host. Host of the machine running Astor4Android. | 
| hostport | Port used to locate Astor4Android at the host. |
| workerip | IP of the machine the AstorWorker will execute. |
| workerport | Port used to locate AstorWorker at it's machine. |
| platformtools | Location of the platform-tools folder. Usually at $ANDROID_HOME/platform-tools. |
| buildtools | Location of the build-tools folder. Usually at $ANDROID_HOME/build-tools/VERSION.
| androidjar | Location of the android.jar. android.jar is usually found at $ANDROID_HOME/platforms/android-VERSION/android.jar), where VERSION is a number.
| key | Key used to sign APKs. You can use **key.jks** located at the main directory of AstorWorker or use your own key. |
| keypassword | Password of the key from the argument -key. For **key.jks**, the password is 123123. |

To execute AstorWorker, follow these instructions:  

1. Build dependencies using Maven and create a file containing their locations separated by a colon:  
	
	`mvn  dependency:build-classpath`  
	`mvn  dependency:build-classpath | egrep -v "(^\[INFO\]|^\[WARNING\])" | tee astorworker-classpath.txt`  

	You can use the same astorworker-classpath.txt for future executions. 

2. Create the local.properties file:

	`echo sdk.dir=$ANDROID_HOME | tee local.properties`  

	You can use the same local.properties for future executions. 

3. Run the command  
   `java -cp $(cat astorworker-classpath.txt):target/classes br.ufg.inf.astorworker.AstorWorker`  
   followed by all the other arguments.  

   Example:  

			java -cp $(cat astorworker-classpath.txt):target/classes br.ufg.inf.astorworker.AstorWorker -hostip 127.0.0.1 -hostport 6665 -workerip 127.0.0.1 -workerport 6666 -platformtools $ANDROID_HOME/platform-tools -buildtools $ANDROID_HOME/build-tools/25.0.0 -androidjar $ANDROID_HOME/platforms/android-25/android.jar -key $(pwd)/key.jks -keypassword 123123  





