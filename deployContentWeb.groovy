def iniciarDeploy(gitUrl){ 

      echo " ---------------------------------------------------------------------------- "
      echo " ---------------------- INICIO DO SCRIPT DEPLOY CONTENT WEB + --------------- "
      echo " ---------------------------------------------------------------------------- "
	  
	 try {
			stage(" Construir ") {
				buildApp(gitUrl)
			}
			
			stage(" Publicar no servidor") {
				deployAppServer();
			}
			
			currentBuild.result = 'SUCCESS'
	  
		} catch (Exception ex) {
			echo "${ex}"
			currentBuild.result = 'FAILURE'
		} finally {
           notificarDeploy(gitUrl)
		}
	}


def buildApp(gitUrl) {

      echo " -------------------------------------- "
      echo " --------- PASSO INICIO CONSTRUÇÃO ----- "
      echo " -------------------------------------- "

      	// URL REPOSITORIO GIT
      	git url: "${gitUrl}",

        // NOME DA BRANCH PROD,DEV,QA...etctra
        branch: "Master",

        // ---------------------------------------------------------------------------------------------- 
        // TOKEN CRIADO NO GIT E ASSOCIADO AO JNEKINS
        // DEVE SER CRIAR UMA CREDENCIAL USANDO USUARIO E SENHA  DO GITHUB :NAME 'github-jenkin'
        // ---------------------------------------------------------------------------------------------- 
        credentialsId: "github-jenkins"

      	// NOME PROPERTIES DA APLICAÇAO.
      	def nomeProperties = "application.properties"

      	// ---------------------------------------------------------------------------------------------- 
      	// PARA USAR O COMANDO READMAVENPOM DEVE INSTALAR O PLUGINS O 'PIPELINE UTILITY STEPS' NO JENKINS 
      	// ----------------------------------------------------------------------------------------------  

      	def pom = readMavenPom file: 'pom.xml'

      	if (pom) {
        	echo "Versão de construção ${pom.version}"
      	} else {
        	echo "Impossível obter as propriedades do projeto. Certifique-se de que seu pom exista."
        	sh "exit 1"
      }

      if (nomeProperties == null) {
        echo "Eh obrigatorio definir o nome do arquivo de configuracao (yml/properties)."
        sh "exit 1"
      }

      // ---------------------------------------------------------------------------------------------- 
      // CONFGIRUAR O JAVA HOME NA MAQUINA ONDE ESTA INSTALADO O JENKINS NO MEU CASO 'JAVA_HOME_08' JRE FOI CONFIGURADO VARIAVEL DE AMBIENTE JAVA 
	  // JAVA_HOME_08 CONFIGURAR NO GLOBLA TOOLS JENKINS
      // CONFGIRUAR O MAVEM HOME NA MAQUINA ONDE ESTA INSTALADO O JENKINS NO MEU CASO 'M3' FOI CONFIGURADO INSTALAR O MAVEM NA MAQUINA VARIAVEL DE AMBIENTE MAVEN
	  // M3 CONFIGURAR NO GLOBLA TOOLS DO JENKINS
      // ---------------------------------------------------------------------------------------------- 
      
	  withEnv(["JAVA_HOME=${ tool 'JAVA_HOME_08' }", "PATH+MAVEN=${tool 'M3'}/bin:${env.JAVA_HOME}/bin"]) {
        sh "rm -f ${pwd()}/src/main/resources/${nomeProperties}"
        sh "mvn --batch-mode -V -U -e clean org.jacoco:jacoco-maven-plugin:prepare-agent verify -Dsurefire.useFile=false -Dskip.failsafe.tests=true"
      }

      if (pom.packaging == 'jar') {
        archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true, onlyIfSuccessful: true
      }

      if (pom.packaging == 'war') {
        archiveArtifacts artifacts: '**/target/*.war', fingerprint: true, onlyIfSuccessful: true
      }
      junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'

      echo " -------------------------------------- "
      echo " -------- PASSO FIM CONTRUÇÃO --------- "
      echo " -------------------------------------- "
    }


// ---------------------------------------------------------------------------------------------- 
// FAZ A LAITURA DO PARAMETROS DO JENKINS  PARAMS.XXX
// CHAMA OS METODOS STOPSERVICE,TRANSFERFILE E STARTSERVICE
// ---------------------------------------------------------------------------------------------- 
def deployAppServer() {
	
  def ambiente = "${params.ambiente}"
  def server = "${params.servidor}"
  def nomeWar = "content-Integracao-web.war"
  def nomeProperties = "application.properties"
  def origemDir = "/var/lib/jenkins/workspace/TCI-CONTENT-WEB/Homologação/JUCEC@2/web/target"
  def destinoDir = "/opt/wildfly/standalone-content-hml/deployments"
  def userNameServer = "root"
	
	 if (ambiente == 'null') {
			echo "EH OBRIGATORIO DEFINIR AMBIENTE NO PARAMETROS DO JNEKINS. VARIAVEL AMBIENTE NÃO PODE SER NULA "
			sh "exit 1"
      }
	  
	 if (server == 'null') {
			echo "EH OBRIGATORIO DEFINIR SERVER NO PARAMETROS DO JNEKINS. VARIAVEL SERVER NÃO PODE SER NULA "
			sh "exit 1"
      }
 
	
  echo "INICIANDO PUBLICAÇÃO EM [${ambiente}] COM O USUÁRIO: ${userNameServer} NO SERVIDOR: ${server}"
  withEnv(["JAVA_HOME=${ tool 'JAVA_HOME_08' }", "PATH+MAVEN=${tool 'M3'}/bin:${env.JAVA_HOME}/bin"]) {

     stopService(userNameServer,server)
     transferFile(nomeWar,origemDir,destinoDir,userNameServer,server) 
     startService(userNameServer,server,nomeWar)
     echo " ----------------------------------------------------------------- "
     echo "--------------FIM DA PUBLICAÇÃO EM [${ambiente}] ----------------- "
     echo " ----------------------------------------------------------------- "
  }
} 


// ---------------------------------------------------------------------------------------------- 
// METODO: QUE FAZ O STOP DA APLICAÇÃO.
// ----------------------------------------------------------------------------------------------  
def stopService(userNameServer,server) {
    try{
	echo " ----------------------------------------------------------------- "
	echo " -------------- STOP SERVICE WILDFLY-CONTENT-HML.SERVICE --------- "
	echo " ----------------------------------------------------------------- "
	sh "sshpass -p @abc123 ssh ${userNameServer}@${server} sudo systemctl stop wildfly-content-hml.service"

	sh "sleep 5"
	} catch (Exception ex) {
            echo "ERRO STOPSERVICE: ${ex}"
			sh "exit 1"
      }	
}

// ---------------------------------------------------------------------------------------------- 
// METODO: QUE FAZ A TRANSFERENCIA DO ARTEFATO ' JAR OU WAR ' DE ORIGEM PARA DESTINO. 
// ---------------------------------------------------------------------------------------------- 
def transferFile(nomeWar,origemDir,destinoDir,userNameServer,server) {
    try{
	echo " --------------------------------------------------------------------------- "
	echo " ------------------------ MOVENDO O ARQUIVO -------------------------------- "
	echo " --------------------------------------------------------------------------- "
	echo " ----------- ARTERFATO ${nomeWar}  ------------"
	echo " ----------- ORIGEM ${origemDir}   ------------"
	echo " ----------- DESTINO ${destinoDir} ------------"
	
	sh "scp ${origemDir}/${nomeWar} ${userNameServer}@${server}:${destinoDir}"
	
	echo " ----------------------------------------------------------------------------------- "
	echo " ---------------------------- TRANSFERIDO COM SUCESSO ------------------------------ "
	echo " ----------------------------------------------------------------------------------- "
	} catch (Exception ex) {
            echo "ERRO AO MOVER O ARQUIVO: ${ex}"
			sh "exit 1"
      }	
}

// ---------------------------------------------------------------------------------------------- 
// METODO: QUE FAZ O START DA APLICAÇÃO. 
// ---------------------------------------------------------------------------------------------- 
def startService(userNameServer,server,nomeWar) {
    try{
	echo " ----------------------------------------------------------------------------------- "
	echo " -------------- INICIALIZANO O SERVIÇO  WILDFLY-CONTENT-HML.SERVICE ---------------- "
	echo " ----------------------------------------------------------------------------------- "
	
	sh "ssh -tt -o StrictHostKeyChecking=no ${userNameServer}@${server} sudo systemctl start wildfly-content-hml.service"
	echo "Pacote ${nomeWar} publicado com sucesso."
	} catch (Exception ex) {
            echo "ERRO AO INICIALIZAR O SERVIÇO: ${ex}"
			sh "exit 1"
      }
}

// ---------------------------------------------------------------------------------------------- 
// METODO: PARA ENVIO DE EMAIL. 
// ---------------------------------------------------------------------------------------------- 
def notificarDeploy(gitUrl){
 try{	
	echo " ----------------------------------------------------------------------------------- "
	echo " -------------------- METODO NOTIFICARDEPLOY TO DO ENVIAR EMAIL -------------------- "
	echo " ----------------------------------------------------------------------------------- "
	} catch (Exception ex) {
            echo "ERRO AO NOTIFICAR DEPLOY : ${ex}"
			sh "exit 1"
      }
}

return this
