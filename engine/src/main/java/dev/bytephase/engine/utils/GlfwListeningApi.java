package dev.bytephase.engine.utils;

import dev.bytephase.engine.render.ListeningGlfw;

public interface GlfwListeningApi {
   boolean canGlfwListening(ListeningGlfw listeningGlfw);

   void handleListeningGlfw(ListeningGlfw listeningGlfw);
}
