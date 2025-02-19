# Reconhecimento de LIBRAS em tempo real

Para acessar o vídeo de execução, os slides apresentados e o .zip do projeto acesse o link [LibrasRecognition](https://drive.google.com/drive/folders/1WHczmqhbQCsuLi3C8IaAS-eoNo6qGrjk?usp=sharing).
É necessário ter o aplicativo Android Studio instalado.
Para rodar o projeto em seu celular é necessário seguir os passos abaixo:

### **1. Ativar a Depuração USB no Celular**

1.  **Acesse as Configurações** do seu celular.
2.  Vá até **Sobre o telefone** e toque em **Número da versão** várias vezes (geralmente 7) até ativar o **Modo Desenvolvedor**.
3.  Retorne para as configurações e entre em **Opções do desenvolvedor**.
4.  Ative **Depuração USB**.

----------

### **2. Conectar o Celular ao Computador**

1.  Use um **cabo USB** para conectar o celular ao PC.
2.  No celular, quando solicitado, selecione **Transferência de Arquivos (MTP)** ou **Somente Carregar**, dependendo do modelo.
3.  Confirme a mensagem **"Permitir depuração USB?"** e marque **Sempre permitir** antes de aceitar.

----------

### **3. Autorizar o Celular no Android Studio**

1.  Abra o **Android Studio**.
2.  Vá até **Run > Select Device** ou clique no botão **Run App (▶️)**.
3.  Selecione seu dispositivo na lista. Se não aparecer, clique em **Troubleshoot device connections**.

----------

### **4. Problemas Comuns**

-   Se o celular não aparece, tente **trocar a porta USB** ou o cabo.
-   Confirme se os **drivers ADB** estão instalados. No Windows, pode ser necessário baixar drivers do site do fabricante.
-   No terminal do Android Studio, tente o comando:   
    
    `adb devices` 
    
    Se o dispositivo aparecer como **unauthorized**, confira o celular para aceitar a depuração.
