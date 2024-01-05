pipeline {
    agent any

    environment {
        TARGET_MACHINE = '10.1.0.4'
        GIT_CREDENTIALS_ID = 'mybizz'
        CREDENTIALS_ID = 'mybizzf'
    }

    stages {
        stage('Clone Code') {
            steps {
                script {
                    // Define the repository URL
                    def repoUrl = 'https://github.com/DDIndia-biz/MyBizz-User.git'
                dir('MyBizz-User') {
                    // Clone code from Git repository using credentials
                    git credentialsId: GIT_CREDENTIALS_ID, url: repoUrl, branch: 'staging-mybizz-user'
                  }
                }
            }
        }
        stage('Deploy Code to Linux Machine') {
            steps {
                script {
                    // Use 'withCredentials' to securely inject the username and password
                    withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID, usernameVariable: 'TARGET_USERNAME', passwordVariable: 'TARGET_PASSWORD')]) {
                        
                        // Use 'bat' step to execute plink command to cache the host key and run the commands
                        bat """
                            echo Connection OK && plink -batch ${env.TARGET_USERNAME}@${env.TARGET_MACHINE} -pw ${env.TARGET_PASSWORD} "bash -c 'if [ -d MyBizz-User ]; then rm -rf MyBizz-User; fi'"
                        """

                        // Use 'bat' step to execute pscp command
                        bat "pscp -r -pw ${env.TARGET_PASSWORD} MyBizz-User ${env.TARGET_USERNAME}@${env.TARGET_MACHINE}:"
                    }
                }
            }   
        }
        stage('Execute Commands on Target Machine') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID, usernameVariable: 'TARGET_USERNAME', passwordVariable: 'TARGET_PASSWORD')]) {

                        // Use 'bat' step to execute plink command to run the commands on the target machine
                        bat """
                            plink -batch ${env.TARGET_USERNAME}@${env.TARGET_MACHINE} -pw ${env.TARGET_PASSWORD} "sudo apt-get update && \
                            sudo apt update && \
                            sudo apt install -y software-properties-common && \
                            sudo add-apt-repository ppa:deadsnakes/ppa && \
                            sudo apt update && \
                            sudo apt install -y python3.10 && \
                            sudo apt install -y python3.10-venv && \
                            python3.10 -m venv venv && \
                            source venv/bin/activate && \
                            sudo apt-get update && \
                            sed -i 's/\\r//' MyBizz-User/requirements.sh && \
                            ./MyBizz-User/requirements.sh && \
                            pip install -r MyBizz-User/requirements.txt && \
                            sudo apt-get install -y python3.10-dev libpq-dev"
                        """
                    }
                }
            }
        }
        stage('Starting the Mybizz with updates pushed') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID, usernameVariable: 'TARGET_USERNAME', passwordVariable: 'TARGET_PASSWORD')]) {
                        catchError(buildResult: 'SUCCESS') {
                            // Use 'bat' step to execute plink command to run the commands on the target machine
                            bat """
                                start /b plink -batch ${env.TARGET_USERNAME}@${env.TARGET_MACHINE} -pw ${env.TARGET_PASSWORD} "source venv/bin/activate && \
                                cd MyBizz-User && \
                                chmod +x packages/linux/* && \
                                nohup python3 manage.py runserver > server.log 2>&1 & disown -h" && timeout /nobreak /t 300
                            """
                        }
                    }
                }
            }
        }
        stage('Checking the deployment reflected in the web browser') {
            steps {
                script {
                    
                    sleep(time: 60, unit: 'SECONDS')
                    
                    powershell """
                        \$url = 'http://${env.TARGET_MACHINE}/'

                        \$response = Invoke-WebRequest -Uri \$url -Method Get

                        if (\$response.StatusCode -eq 200) {
                            Write-Host 'Target IP responded with HTTP 200 OK.'
                        } else {
                            Write-Host 'Target IP did not respond with HTTP 200 OK. Status code:' \$response.StatusCode
                        }
                    """
                }
            }
        }
    }
}

