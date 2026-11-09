# Estado e roadmap — Voz99 (sem Firebase)

| Capacidade | Estado | Observação |
|---|---|---|
| Compose: conversa / favoritos / ajustes | Código implementado | Compilação Android real ainda não verificada |
| Gemini API por HTTPS | Código implementado | Chave do Google AI Studio cadastrada no app |
| Criptografia Android Keystore | Código implementado | Chave local protegida em repouso; uso direto apenas em teste privado |
| SpeechRecognizer / TTS | Código implementado | Conversa por turnos, não full-duplex |
| GPS e destinos favoritos Room | Código implementado | Testar permissões em ambos os aparelhos |
| Abrir 99 oficial | Código implementado | Abre pelo launcher, sem deep link de corrida |
| Destacar PIN na 99 | Prova de conceito | Permissão de acessibilidade e UI da 99 podem impedir |
| Solicitar / cancelar / acompanhar corridas reais | Não integrado | Requer solução de integração autorizada pela 99 |
| Consulta de preços e motorista | Não integrado | Não há API de conta pessoal incorporada |
| IA offline | Não | Gemini requer internet |

**Próximo teste:** sincronizar Gradle no Android Studio, ativar a chave sem faturamento, enviar mensagem pelo app e testar conversação por voz. Depois instalar nos dois dispositivos.
