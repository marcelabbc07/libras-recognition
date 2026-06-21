# Libras Recognition (LibrasApp)

Aplicativo Android nativo (**Java**) que reconhece gestos do **alfabeto manual em Libras (datilologia)** em tempo real: usa **CameraX** para o vídeo, **MediaPipe Hands** para detecção e landmarks da mão e **TensorFlow Lite** com modelo **YOLO** (`best_float32.tflite`) para classificação **no dispositivo** (funciona **offline** após instalar o app).

---

## O que precisa no computador

| Requisito | Nota |
|-----------|------|
| **Android Studio** | Versão recente (recomendado **2024.1+**), com Android SDK e **SDK Platform 34** instalados. |
| **JDK 17** | O Android Gradle Plugin **8.6** usa JDK 17. No Android Studio: *File → Settings → Build, Execution, Deployment → Build Tools → Gradle* → **Gradle JDK** = **17** (ou “Embedded JDK”). |
| **Git** | Para clonar o repositório. |
| **Cabo USB** | Para instalar e depurar no telefone (recomendado). |

No **telefone**:

- **Android 5.0 ou superior** (`minSdk 21`).
- **Câmera** (frontal ou traseira).
- Espaço livre para o APK.

---

## 1. Clonar o repositório

```bash
git clone https://github.com/marcelabbc07/libras-recognition.git
cd libras-recognition
```

> **Modelo:** após o clone devem existir `app/src/main/assets/best_float32.tflite` e `app/src/main/assets/labels.txt`. Se o `.tflite` estiver em **Git LFS**, execute também `git lfs pull`.

---

## 2. Abrir no Android Studio

1. **File → Open** → pasta **`libras-recognition`** (onde estão `settings.gradle` e a pasta `app`).
2. Aguarde o **Gradle Sync** terminar (a primeira vez pode demorar).

---

## 3. Preparar o celular (depuração USB)

1. **Definições → Sobre o telefone** → toque várias vezes em **Número da compilação** até aparecerem as **Opções de programador**.
2. **Opções de programador** → **Depuração USB** = ligado.
3. Ligue o cabo; no telefone aceite **“Permitir depuração USB?”** para o PC.

No **Windows**, se o telefone não aparecer no Android Studio, instale o **driver USB** do fabricante.

---

## 4. Executar no celular

1. No topo do Android Studio, escolha o **dispositivo físico** (recomendado para testar a câmera real).
2. Clique **Run** ▶ (*Run 'app'*) ou **Shift+F10**.

Na primeira abertura, aceite a permissão de **câmera**.

---

## 5. Uso rápido no app

- Overlay com **landmarks** quando a mão é detetada.
- Texto do gesto em **tempo real**, **sem internet** para o modelo.
- Botão para alternar **câmara frontal / traseira**.
- Menu **Ajuda** (FAQ).

---

## 6. Código importante

| Caminho | Função |
|---------|--------|
| `app/src/main/java/com/example/librasrecognition/MainActivity.java` | Câmera, permissões, UI |
| `app/src/main/java/com/example/librasrecognition/HandDetectionProcessor.java` | Pipeline câmera → MediaPipe → classificador |
| `app/src/main/java/com/example/librasrecognition/GestureClassifier.java` | TensorFlow Lite / YOLO |
| `app/src/main/java/com/example/librasrecognition/HandOverlayView.java` | Desenho da mão |
| `app/src/main/assets/` | `best_float32.tflite`, `labels.txt` |

---

## 7. Classes do modelo

Os gestos seguem **`labels.txt`** (25 classes na versão atual). O `.tflite` e o `labels.txt` devem estar **alinhados**.

---

## 8. Problemas comuns

| Sintoma | O que tentar |
|---------|----------------|
| Erro de **Gradle / sync** | JDK **17** para o Gradle; *File → Invalidate Caches / Restart*. |
| Celular **não aparece** | Cabo, drivers USB, depuração USB; na linha de comandos: `adb devices`. |
| **INSTALL_FAILED** | Desinstale uma versão antiga com o mesmo `applicationId` ou use `adb install -r`. |
| **Câmera preta / crash** | Permissões; feche outras apps que usem a câmera; reinicie o telefone. |
| **Inferência lenta** | Aparelhos mais antigos têm GPU/CPU mais limitadas; boa iluminação e mão bem enquadrada ajudam. |

---
