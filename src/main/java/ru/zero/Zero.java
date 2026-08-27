package ru.zero;

import java.io.File;
import lombok.Generated;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import ru.zero.compat.LunarCompat;
import ru.zero.compat.LunarOverlayRender;
import ru.zero.cfg.ConfigManager;
import ru.zero.client.MenuKeyHandler;
import ru.zero.client.ZeroKeyBindings;
import ru.zero.commands.CommandBootstrap;
import ru.zero.config.GuiManager;
import ru.zero.config.friend.FriendManager;
import ru.zero.config.target.TargetManager;
import ru.zero.event.EventManager;
import ru.zero.event.RenderHandler;
import ru.zero.event.render.RenderEvent;
import ru.zero.linking.VisualLinkingClient;
import ru.zero.module.api.Manager;
import ru.zero.module.bind.BindingManager;
import ru.zero.module.impl.visuals.Hud;
import ru.zero.rpc.RPC;
import ru.zero.sound.SoundMixFilter;
import ru.zero.ui.draggable.DraggableManager;
import ru.zero.ui.gui.GuiClient;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.util.render.animation.AnimationSystem;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.client.Lang;
import ru.zero.util.render.backends.RenderBackend;
import ru.zero.util.render.backends.gl.GlBackend;
import ru.zero.util.render.backends.gl.GlState;
import ru.zero.util.render.backends.vk.VulkanBackend;
import ru.zero.util.render.capture.EntityFramebufferCaptureManager;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.texture.TextureLoader;
import ru.zero.util.render.text.FontObject;
import ru.zero.util.render.utils.SoundUtil;
import ru.zero.util.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
public class Zero implements ClientModInitializer {
   public static Zero get;
   public Manager manager;
   public final String name = "ZeroDLC";
   public final String version = "v1";
   public final String title = "1.21.4";
   public final File preRoot = new File(System.getProperty("user.home"), ".zerodlc");
   public final File root = new File(this.preRoot, "ZeroDLC");
   public final String rootRes = "zero";
   public static SoundMixFilter rtx;
   public GuiManager guiManager;
   public ConfigManager configManager;
   public FriendManager friendManager;
   public TargetManager targetManager;
   public GuiClient guiClient;
   public VisualLinkingClient visualLinkingClient;
   private final RPC rpc = new RPC();
   private static RenderBackend backend;
   private static Renderer2D renderer;
   private static FontObject uiFont;
   private static volatile boolean initialized = false;
   private static volatile boolean modInitialized = false;

   public static Renderer2D getRenderer() {
      ensureRendererInitialized();
      return renderer;
   }

   public static RenderBackend getBackend() {
      ensureRendererInitialized();
      return backend;
   }

   public static boolean isVulkanBackend() {
      return backend instanceof ru.zero.util.render.backends.vk.VulkanBackend;
   }

   public static ru.zero.util.render.backends.vk.VulkanBackend getVulkanBackend() {
      return isVulkanBackend() ? (ru.zero.util.render.backends.vk.VulkanBackend) backend : null;
   }

   /** Vulkan glass compositing is not initialized in this renderer configuration. */
   public static ru.zero.util.render.backends.vk.VulkanGlassCoordinator getGlassCoordinator() {
      return null;
   }

   public static GlBackend getGlBackend() {
      return backend instanceof GlBackend ? (GlBackend)backend : null;
   }

   public static boolean isModInitialized() {
      return modInitialized;
   }

   public void onInitializeClient() {
      System.out.println("[Zero] onInitializeClient() START");
      if (FabricLoader.getInstance().isModLoaded("night")) {
         throw new IllegalStateException(
               "Mod \"night\" is loaded together with \"zero\". Remove night*.jar from mods/ — only one client build is allowed.");
      }

      get = this;
      migrateLegacyRoot();
      if (!this.root.exists()) {
         this.root.mkdirs();
      }
      this.manager = new Manager();
      this.friendManager = new FriendManager();
      FriendManager.init();
      this.targetManager = new TargetManager();
      TargetManager.init();
      this.configManager = new ConfigManager();
      this.guiManager = new GuiManager();
      this.guiManager.init();
      ru.zero.ui.gui.color.ColorPresetStorage.load();
        GuiScreen.clientBlurSetting.set(this.guiManager.isGuiBlurEnabled());
        GuiScreen.clientVanillaSetting.set(this.guiManager.isGuiVanillaStyleEnabled());
        GuiScreen.clientSoundSetting.set(this.guiManager.isGuiSoundEnabled());
       GuiScreen.clientLiquidGlassSetting.set(this.guiManager.isGuiLiquidGlassEnabled());
      if (!Boolean.parseBoolean(System.getProperty("zero.glass.vulkan", "true"))) {
         GuiScreen.clientLiquidGlassSetting.set(false);
      }
      GuiScreen.selectedTheme = this.guiManager.getCurrentTheme();
      GuiScreen.preSelectedTheme = this.guiManager.getCurrentTheme();
      GuiScreen.selectedCategories = this.guiManager.getCurrentCategory();
      rtx = SoundMixFilter.makeDistorterMixer();
      rtx.init();
      CommandBootstrap.initialize();
      Lang.load();
      ZeroKeyBindings.register();
      MenuKeyHandler.register();
      BindingManager.getInstance().initialize();
      if (this.configManager != null) {
         this.configManager.load();
         boolean loadedAutoSave = false;
         for (String autoName : ConfigManager.AUTO_SAVE_ALIASES) {
            if (this.configManager.findConfig(autoName) != null && this.configManager.loadConfig(autoName)) {
               loadedAutoSave = true;
               break;
            }
         }
         if (!loadedAutoSave && this.configManager.findConfig("default") != null) {
            this.configManager.loadConfig("default");
         }
      }

      ZeroKeyBindings.syncFromMenuModule();

      Hud hudModule = this.manager != null ? this.manager.get(Hud.class) : null;
      if (hudModule != null && !hudModule.enable) {
         hudModule.setState(true);
         if (this.configManager != null) {
            this.configManager.autoSave();
         }
      }

      this.guiClient = new GuiClient();
      this.visualLinkingClient = new VisualLinkingClient();
      LunarCompat.applyStartupTweaks();
      if (LunarCompat.isLunarClient() && this.manager != null) {
         Hud hud = this.manager.get(Hud.class);
         if (hud != null) {
            Hud.blur.set(false);
         }
      }
      RenderHandler.register();
      EventManager.register(this);
      EventManager.register(this.visualLinkingClient);
      ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdownClientResources());
      modInitialized = true;
      System.out.println("[Zero] onInitializeClient() COMPLETE - modInitialized=" + modInitialized);
   }

   private static void shutdownClientResources() {
      if (get != null && get.configManager != null) {
         get.configManager.flushAutoSave();
      }

      if (get != null && get.rpc != null) {
         get.rpc.stopRpc();
      }

      ConfigManager.shutdown();
      ColorUtil.shutdownCacheCleaner();
      SoundUtil.releaseAll();
      EntityFramebufferCaptureManager.getInstance().setEnabled(false);
      TextureLoader.clearCache();
      if (backend != null) {
         backend.destroy();
         backend = null;
      }
      initialized = false;
      renderer = null;
   }

   private void migrateLegacyRoot() {
      try {
         File oldPreRoot = new File(System.getProperty("user.home"), ".nightdlc");
         File oldRoot = new File(oldPreRoot, "NightDLC");
         if (oldRoot.exists() && !this.root.exists()) {
            if (!this.preRoot.exists()) {
               this.preRoot.mkdirs();
            }
            // best-effort rename
            oldRoot.renameTo(this.root);
         }
      } catch (Exception ignored) {
      }
   }

   public static void ensureRendererInitialized() {
      if (LunarCompat.shouldDeferGpuInit()) {
         return;
      }
      if (!initialized) {
         onInit();
      }
   }

   public static synchronized void reloadVisualRenderer() {
      EntityFramebufferCaptureManager.getInstance().setEnabled(false);
      TextureLoader.clearCache();
      FontRegistry.shutdown();
      if (backend != null) {
         backend.destroy();
         backend = null;
      }
      renderer = null;
      uiFont = null;
      initialized = false;
      onInit();
   }

   private static synchronized void onInit() {
      if (!initialized) {
         backend = createBackend();
         TextureLoader.initialize(backend);
         renderer = new Renderer2D(backend);
         FontRegistry.initialize(backend, renderer);
         uiFont = FontRegistry.INTER_MEDIUM;
         initialized = true;
         System.out.println("[Zero] Visual renderer initialized (backend=" + backend.getClass().getSimpleName() + ")");
      }
   }

   private static RenderBackend createBackend() {
      // HUD blur is implemented by the OpenGL post-processing pipeline. Vulkan is
      // still opt-in, because mixing its final composite with GL blur can replace
      // the HUD render target on some drivers.
      String requested = System.getProperty("zero.renderer", "gl").trim();
      boolean wantGl = requested.equalsIgnoreCase("gl");
      if (!wantGl) {
         try {
            RenderBackend vk = new VulkanBackend();
            if (((VulkanBackend) vk).isVulkanReady()) {
               System.out.println("[Zero] Using Vulkan render backend (default). Use -Dzero.renderer=gl to force GL.");
               return vk;
            }
            System.err.println("[Zero] Vulkan backend not ready, falling back to GL");
            vk.destroy();
         } catch (Throwable t) {
            System.err.println("[Zero] Vulkan backend creation failed, falling back to GL: " + t.getMessage());
            t.printStackTrace();
         }
      }
      System.out.println("[Zero] Using OpenGL render backend");
      return new GlBackend();
   }

   public static void onRender() {
      if (modInitialized) {
         GlState.Snapshot snapshot = GlState.push();

         try {
            if (!initialized && !LunarCompat.shouldDeferGpuInit()) {
               onInit();
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
               return;
            }

            int width = client.getWindow().getFramebufferWidth();
            int height = client.getWindow().getFramebufferHeight();
            if (width <= 0 || height <= 0) {
               return;
            }

            if (!initialized) {
               return;
            }

            boolean lunarMenu = LunarCompat.isLunarClient() && LunarOverlayRender.isZeroMenuOpen(client);

            if (LunarCompat.isLunarClient() && !lunarMenu && !LunarOverlayRender.canRenderInGameHudOverlay(client)) {
               return;
            }

            AnimationSystem.getInstance().tick();
            DraggableManager draggableManager = DraggableManager.getInstance();
            draggableManager.beginFrame(client, renderer, width, height);
            boolean rendererBegun = false;

            try {
               renderer.begin(width, height);
               rendererBegun = true;

               try {
                  if (lunarMenu) {
                     LunarOverlayRender.renderMenuInActiveFrame(renderer, client);
                  } else {
                     EventManager.call(new RenderEvent(client, renderer, uiFont, width, height));
                  }
               } finally {
                  if (rendererBegun) {
                     renderer.end();
                  }
               }
            } finally {
               draggableManager.endFrame();
            }
         } finally {
            GlState.pop(snapshot);
         }
      }
   }

   @Generated
   public Manager getManager() {
      return this.manager;
   }

   @Generated
   public String getName() {
      return "ZeroDLC";
   }

   @Generated
   public String getVersion() {
      return "v1";
   }

   @Generated
   public String getTitle() {
      return "1.21.4";
   }

   @Generated
   public File getPreRoot() {
      return this.preRoot;
   }

   @Generated
   public File getRoot() {
      return this.root;
   }

   @Generated
   public String getRootRes() {
      return "zero";
   }

   @Generated
   public GuiManager getGuiManager() {
      return this.guiManager;
   }

   @Generated
   public ConfigManager getConfigManager() {
      return this.configManager;
   }

   @Generated
   public FriendManager getFriendManager() {
      return this.friendManager;
   }

   @Generated
   public GuiClient getGuiClient() {
      return this.guiClient;
   }

   @Generated
   public VisualLinkingClient getVisualLinkingClient() {
      return this.visualLinkingClient;
   }

   @Generated
   public RPC getRpc() {
      return this.rpc;
   }

   @Generated
   public void setManager(Manager manager) {
      this.manager = manager;
   }

   @Generated
   public void setGuiManager(GuiManager guiManager) {
      this.guiManager = guiManager;
   }

   @Generated
   public void setConfigManager(ConfigManager configManager) {
      this.configManager = configManager;
   }

   @Generated
   public void setFriendManager(FriendManager friendManager) {
      this.friendManager = friendManager;
   }

   @Generated
   public void setGuiClient(GuiClient guiClient) {
      this.guiClient = guiClient;
   }

   @Generated
   public void setVisualLinkingClient(VisualLinkingClient visualLinkingClient) {
      this.visualLinkingClient = visualLinkingClient;
   }
}
