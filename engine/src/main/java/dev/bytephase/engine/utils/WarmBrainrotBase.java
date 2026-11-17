package dev.bytephase.engine.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import dev.bytephase.engine.utils.BytephaseclientWarm;
import dev.bytephase.engine.utils.LatheLoft;
import dev.bytephase.engine.utils.PoseProgress;
import dev.bytephase.engine.utils.ForeShinScale;

public abstract class WarmBrainrotBase {
   private volatile BytephaseclientWarm classBytephaseclientWarm;
   private final AtomicBoolean atomicBoolean = new AtomicBoolean();
   private volatile BytephaseclientWarm classBytephaseclient;
   private volatile BytephaseclientWarm classThrowableThread;
   private volatile Throwable throwable;
   private static final ExecutorService threadExecutor = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "bytephase-brainrot-warm");
      thread.setDaemon(true);
      thread.setPriority(1);
      return thread;
   });

   public abstract String formatTungDinString();

   public float getAmount(BytephaseclientWarm bytephaseWarm) {
      return bytephaseWarm.bytephaseWarmAmount + 0.42F * (bytephaseWarm.boundsMaxY - bytephaseWarm.bytephaseWarmAmount);
   }

   public String formatNoteBlock() {
      return null;
   }

   public final BytephaseclientWarm getClassThrowableThread() {
      return this.classThrowableThread;
   }

   public final Throwable getThrowable() {
      return this.throwable;
   }

   public abstract String formatTungDin();

   public final synchronized BytephaseclientWarm getClassClassBytephaseclientWarm() {
      if (this.classBytephaseclientWarm == null) {
         this.classBytephaseclientWarm = this.getClassForFloatIntArray(1.0F, null);
      }

      return this.classBytephaseclientWarm;
   }

   public int getValue() {
      return 1;
   }

   protected abstract void handleShinFoot(LatheLoft latheLoft);

   public final synchronized BytephaseclientWarm getBytephaseclient() {
      if (this.classBytephaseclient == null) {
         this.classBytephaseclient = this.getClassForFloatIntArray(0.5F, this.getClassClassBytephaseclientWarm().bytephaseValues);
      }

      return this.classBytephaseclient;
   }

   public final BytephaseclientWarm getClassBytephaseclient() {
      return this.classBytephaseclient;
   }

   public float getAmountFloat() {
      return 1.0F;
   }

   public abstract void handleShinProgress(PoseProgress poseProgress, ForeShinScale ifAbsent);

   protected int getValueInt() {
      return 512;
   }

   private BytephaseclientWarm getClassForFloatIntArray(float classValue, int[] classValues) {
      LatheLoft latheLoft = new LatheLoft(this.getValueInt(), this.getValueInt(), classValue, classValues != null);
      this.handleShinFoot(latheLoft);
      return latheLoft.getMax(this.formatTungDin(), classValues);
   }

   public final void updateThrowableThread() {
      if (this.classThrowableThread == null && this.throwable == null && this.atomicBoolean.compareAndSet(false, true)) {
         threadExecutor.execute(() -> {
            try {
               this.getBytephaseclientClassThrowableThread();
            } catch (Throwable e) {
               this.throwable = e;
            }
         });
      }
   }

   public final synchronized BytephaseclientWarm getBytephaseclientClassThrowableThread() {
      if (this.classThrowableThread == null) {
         this.getBytephaseclient();
         this.classThrowableThread = this.getClassForFloatIntArray(0.25F, this.getClassClassBytephaseclientWarm().bytephaseValues);
      }

      return this.classThrowableThread;
   }

   public final BytephaseclientWarm getClassBytephaseclientWarm() {
      return this.classBytephaseclientWarm;
   }
}
