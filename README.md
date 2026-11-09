# Voz99 — versão simplificada sem Firebase

**Aplicativo Android nativo (Kotlin + Jetpack Compose)** para apoiar pessoas que têm dificuldade de solicitar corridas no aplicativo da 99. Desenvolvido inicialmente para testes privados no **Samsung Galaxy A54** e **Motorola Moto G06**.

## O que foi simplificado

A versão anterior exigia criar projeto no Firebase, registrar um aplicativo e baixar `google-services.json`. **Esta versão NÃO usa Firebase e NÃO pede `google-services.json`.** A conversa com IA utiliza a **API Gemini Developer diretamente, em modo de teste**, por HTTPS. Para ativar, é necessário apenas obter uma chave gratuita no Google AI Studio e digitá-la no próprio aplicativo, na tela **Ajustes**.

⚠️ **A conta Google e a chave precisam ser criadas pelo titular.** Não inclua uma chave no repositório, não envie por chat e não a distribua para pessoas desconhecidas. A chave é criptografada e guardada localmente via Android Keystore, mas aplicativos clientes não conseguem proteger uma credencial tão bem quanto um backend. **Este modelo é só para os dois celulares de teste.** Para distribuição mais ampla, usar backend próprio ou Firebase AI Logic com App Check.

## Instalação (Windows / Android Studio)

1. Extraia o ZIP e, no Android Studio, use **File > Open** na pasta `Voz99_Sem_Firebase` (não crie outro projeto).
2. Aguarde **Gradle Sync** com internet. Este repositório mantém um bootstrap verificado do Gradle Wrapper: se o Studio não baixar o wrapper automaticamente, abra o Terminal do Android Studio na raiz do projeto e execute `gradlew.bat --version` para baixar o JAR oficial e a distribuição. **Não foi possível anexar o JAR oficial nem compilar o APK neste ambiente.**
3. Use o emulador ou conecte um telefone Android e clique em **Run**. Para gerar APK: **Build > Build App Bundle(s) / APK(s) > Build APK(s)**. O arquivo costuma aparecer em `app/build/outputs/apk/debug/app-debug.apk`.
4. O app abre **sem chave** em modo de demonstração, para testar interface, microfone, voz, GPS e favoritos.

## Ativar Gemini real — única configuração externa

1. Entre em **https://aistudio.google.com/api-keys** com sua conta Google.
2. Crie uma **chave da API Gemini** em um projeto com **plano gratuito, sem faturamento**. Não ative modalidade paga. O modelo selecionado é `gemini-2.5-flash`, que tem modalidade gratuita sujeita às cotas e à disponibilidade regional.
3. No APK, abra **Ajustes → Chave da API Gemini**, cole a chave e toque em **Ativar IA neste celular**. Faça o mesmo em cada telefone, se ambos forem utilizar a mesma chave/projeto.
4. Na aba **Conversa**, digite primeiro **“Olá, preciso ir à farmácia”**. A IA deve responder em português. Depois teste o botão de voz.

**Não é necessário criar Firebase, `google-services.json`, cadastrar Android package ou montar servidor.** O pacote Android deste projeto é `br.com.voz99`.

Os serviços nativos Android de voz não têm custo de licença no aplicativo, mas o motor do celular pode depender da internet. O envio de texto ao Gemini depende de internet. O plano gratuito tem cotas; evite dados pessoais reais ou informações sensíveis, pois o tratamento de dados segue as condições do nível gratuito da API.

## Funcionalidades implementadas no código

- Interface Compose de conversa, destinos favoritos (Room), ajustes e rascunho de viagem.
- Integração com Gemini 2.5 Flash via HTTPS/JSON; contexto de conversa e respostas curtas.
- Voz com `SpeechRecognizer` + `TextToSpeech`, em turnos: o microfone reabre após a resposta falada enquanto a sessão estiver ativa.
- Uso opcional do GPS do aparelho para sugerir a origem, que deve ser conferida.
- Abertura do aplicativo oficial 99 instalado no telefone.
- Protótipo opcional de `AccessibilityService` para tentar destacar visualmente um PIN reconhecido na 99 durante 2 minutos, com ativação explícita nas Configurações do Android.

## Limites importantes

- **Não solicita, cancela nem paga corridas automaticamente.** Não existe integração de API pessoal autorizada neste projeto.
- A 99 abre na tela que seu próprio aplicativo determinar. Não há deep link validado para abrir a corrida atual.
- Destacar PIN é experimental e depende de o elemento aparecer na árvore de acessibilidade da versão instalada da 99.
- IA não inventa preços, horários, PIN, motorista nem confirmações de corridas.
- **Código-fonte ainda não validado em build Android real** nesta entrega. Se o Studio mostrar erro de Gradle ou Kotlin, é preciso corrigi-lo conforme o log do ambiente.

## Organização

```
app/src/main/java/br/com/voz99/
  MainActivity.kt
  ai/GeminiConversation.kt          Chamada REST e prompt da IA
  ai/DemoConversation.kt            Demonstração sem chave
  data/GeminiKeyStore.kt             Chave criptografada no Android Keystore
  data/FavoritesDatabase.kt         Room de destinos
  data/UserPreferences.kt           Preferências locais
  location/LocationHelper.kt        GPS / geocodificação
  accessibility/PinHighlightService.kt
  platform/NinetyNineLauncher.kt
  ui/MainViewModel.kt
  ui/Voz99Screen.kt
  voice/AndroidSpeech.kt
```

## Custos

Código e distribuição por APK: R$ 0. Gemini 2.5 Flash no nível gratuito: R$ 0 até os limites atuais, sem ativar faturamento. Serviços de transporte contratados pela 99 são cobrados pela 99. Consulte preços e cotas em https://ai.google.dev/gemini-api/docs/pricing.
