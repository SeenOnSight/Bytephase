package dev.bytephase.engine.utils;

import dev.bytephase.engine.config.SchematicLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import dev.bytephase.engine.utils.JsonMirror;

public final class RecordingsUnnamedUtils {
   public static Path getResolve(String requestedName) {
      String baseName = requestedName != null && !requestedName.isBlank() ? requestedName : "unnamed";
      int lastIndexOf = baseName.lastIndexOf(46);
      if (lastIndexOf > 0) {
         baseName = baseName.substring(0, lastIndexOf);
      }

      return SchematicLoader.getSchematicsDir().resolve("recordings").resolve(baseName + ".build.json");
   }

   private RecordingsUnnamedUtils() {
   }

   public static JsonMirror getJsonMirror(String jsonMirrorText) {
      try {
         Path resolve = getResolve(jsonMirrorText);
         return !Files.exists(resolve) ? null : JsonMirror.getMirrorFormat(Files.readString(resolve));
      } catch (Exception e) {
         return null;
      }
   }
}
