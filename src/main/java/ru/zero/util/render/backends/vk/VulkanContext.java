package ru.zero.util.render.backends.vk;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtent3D;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import ru.zero.util.other.PlatformUtil;

@Environment(EnvType.CLIENT)
public final class VulkanContext {
   private VkInstance instance;
   private VkPhysicalDevice physicalDevice;
   private VkDevice device;
   private VkQueue graphicsQueue;
   private long commandPoolHandle;
   private int graphicsQueueFamily = -1;
   private VkPhysicalDeviceMemoryProperties memoryProperties;
   private VkPhysicalDeviceProperties physicalDeviceProperties;
   private boolean externalMemoryWin32;
   private boolean externalSemaphoreWin32;
   private boolean created;

   public VkInstance instance() {
      return this.instance;
   }

   public VkPhysicalDevice physicalDevice() {
      return this.physicalDevice;
   }

   public VkDevice device() {
      return this.device;
   }

   public VkQueue graphicsQueue() {
      return this.graphicsQueue;
   }

   public long commandPoolHandle() {
      return this.commandPoolHandle;
   }

   public int graphicsQueueFamily() {
      return this.graphicsQueueFamily;
   }

   public VkPhysicalDeviceMemoryProperties memoryProperties() {
      return this.memoryProperties;
   }

   public String deviceName() {
      return this.physicalDeviceProperties != null ? this.physicalDeviceProperties.deviceNameString() : "unknown";
   }

   public boolean supportsExternalMemoryWin32() {
      return this.externalMemoryWin32;
   }

   public boolean supportsExternalSemaphoreWin32() {
      return this.externalSemaphoreWin32;
   }

   public boolean isCreated() {
      return this.created;
   }

   public int findMemoryType(int typeBits, int properties) {
      for (int i = 0; i < this.memoryProperties.memoryTypeCount(); i++) {
         if ((typeBits & (1 << i)) != 0
               && (this.memoryProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
            return i;
         }
      }
      return -1;
   }

   public void create() {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         this.createInstance(stack);
         this.pickPhysicalDevice(stack);
         this.detectInteropExtensions(stack);
         this.createDevice(stack);
         this.createCommandPool(stack);
      }
      this.created = true;
      System.out.println("[Zero/Vulkan] Context ready: device=\"" + this.deviceName() + "\", graphicsQueueFamily="
            + this.graphicsQueueFamily + ", extMemWin32=" + this.externalMemoryWin32 + ", extSemWin32="
            + this.externalSemaphoreWin32);
   }

   private void createInstance(MemoryStack stack) {
      VkApplicationInfo app = VkApplicationInfo.calloc(stack);
      app.sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO);
      app.pApplicationName(stack.UTF8("Zero"));
      app.applicationVersion(VK13.VK_MAKE_API_VERSION(0, 2, 3, 0));
      app.pEngineName(stack.UTF8("ZeroRenderer"));
      app.engineVersion(VK13.VK_MAKE_API_VERSION(0, 1, 0, 0));
      app.apiVersion(VK13.VK_MAKE_API_VERSION(0, 1, 1, 0));

      PointerBuffer extensions = this.collectInstanceExtensions(stack);

      VkInstanceCreateInfo ci = VkInstanceCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
      ci.pApplicationInfo(app);
      ci.ppEnabledExtensionNames(extensions);

      PointerBuffer pInstance = stack.mallocPointer(1);
      int err = VK10.vkCreateInstance(ci, null, pInstance);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateInstance failed: " + vulkanError(err));
      }
      this.instance = new VkInstance(pInstance.get(0), ci);
      System.out.println("[Zero/Vulkan] vkCreateInstance OK");
   }

   private PointerBuffer collectInstanceExtensions(MemoryStack stack) {
      PointerBuffer buffer = stack.mallocPointer(2);
      buffer.put(stack.UTF8(org.lwjgl.vulkan.KHRSurface.VK_KHR_SURFACE_EXTENSION_NAME));
      if (PlatformUtil.isWindows()) {
         buffer.put(stack.UTF8(org.lwjgl.vulkan.KHRWin32Surface.VK_KHR_WIN32_SURFACE_EXTENSION_NAME));
      }
      buffer.flip();
      return buffer;
   }

   private void pickPhysicalDevice(MemoryStack stack) {
      IntBuffer pCount = stack.mallocInt(1);
      int err = VK10.vkEnumeratePhysicalDevices(this.instance, pCount, null);
      if (err != VK10.VK_SUCCESS || pCount.get(0) == 0) {
         throw new IllegalStateException("vkEnumeratePhysicalDevices failed: " + vulkanError(err));
      }
      int count = pCount.get(0);
      PointerBuffer pDevices = stack.mallocPointer(count);
      VK10.vkEnumeratePhysicalDevices(this.instance, pCount, pDevices);

      VkPhysicalDevice best = null;
      int bestQueueFamily = -1;
      for (int i = 0; i < count; i++) {
         VkPhysicalDevice pd = new VkPhysicalDevice(pDevices.get(i), this.instance);
         int qf = this.findGraphicsQueueFamily(stack, pd);
         if (qf >= 0) {
            best = pd;
            bestQueueFamily = qf;
            break;
         }
      }
      if (best == null) {
         throw new IllegalStateException("No Vulkan physical device with a graphics queue family found");
      }
      this.physicalDevice = best;
      this.graphicsQueueFamily = bestQueueFamily;

      this.physicalDeviceProperties = VkPhysicalDeviceProperties.calloc();
      VK10.vkGetPhysicalDeviceProperties(this.physicalDevice, this.physicalDeviceProperties);
      this.memoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
      VK10.vkGetPhysicalDeviceMemoryProperties(this.physicalDevice, this.memoryProperties);
      System.out.println("[Zero/Vulkan] Selected physical device: \"" + this.physicalDeviceProperties.deviceNameString()
            + "\" (queueFamily=" + this.graphicsQueueFamily + ")");
   }

   private int findGraphicsQueueFamily(MemoryStack stack, VkPhysicalDevice pd) {
      IntBuffer pCount = stack.mallocInt(1);
      VK10.vkGetPhysicalDeviceQueueFamilyProperties(pd, pCount, null);
      int count = pCount.get(0);
      VkQueueFamilyProperties.Buffer props = VkQueueFamilyProperties.calloc(count, stack);
      VK10.vkGetPhysicalDeviceQueueFamilyProperties(pd, pCount, props);
      for (int i = 0; i < count; i++) {
         if ((props.get(i).queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0) {
            return i;
         }
      }
      return -1;
   }

   private void detectInteropExtensions(MemoryStack stack) {
      IntBuffer pCount = stack.mallocInt(1);
      int err = VK10.vkEnumerateDeviceExtensionProperties(this.physicalDevice, (String) null, pCount, null);
      if (err != VK10.VK_SUCCESS) {
         System.err.println("[Zero/Vulkan] vkEnumerateDeviceExtensionProperties (count) failed: " + vulkanError(err));
         return;
      }
      int count = pCount.get(0);
      org.lwjgl.vulkan.VkExtensionProperties.Buffer props =
            org.lwjgl.vulkan.VkExtensionProperties.calloc(count, stack);
      err = VK10.vkEnumerateDeviceExtensionProperties(this.physicalDevice, (String) null, pCount, props);
      if (err != VK10.VK_SUCCESS) {
         System.err.println("[Zero/Vulkan] vkEnumerateDeviceExtensionProperties (data) failed: " + vulkanError(err));
         return;
      }
      boolean extMem = false;
      boolean extSem = false;
      boolean extMemWin32 = false;
      boolean extSemWin32 = false;
      for (int i = 0; i < count; i++) {
         String name = props.get(i).extensionNameString();
         if (name.equals(org.lwjgl.vulkan.KHRExternalMemory.VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME)) {
            extMem = true;
         } else if (name.equals(org.lwjgl.vulkan.KHRExternalSemaphore.VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME)) {
            extSem = true;
         } else if (name.equals(org.lwjgl.vulkan.KHRExternalMemoryWin32.VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME)) {
            extMemWin32 = true;
         } else if (name
               .equals(org.lwjgl.vulkan.KHRExternalSemaphoreWin32.VK_KHR_EXTERNAL_SEMAPHORE_WIN32_EXTENSION_NAME)) {
            extSemWin32 = true;
         }
      }
      this.externalMemoryWin32 = extMem && extMemWin32;
      this.externalSemaphoreWin32 = extSem && extSemWin32;
      System.out.println("[Zero/Vulkan] Device extensions: external_memory=" + extMem + ", external_semaphore=" + extSem
            + ", win32_memory=" + extMemWin32 + ", win32_semaphore=" + extSemWin32);
   }

   private void createDevice(MemoryStack stack) {
      java.nio.FloatBuffer pPriorities = stack.callocFloat(1);
      pPriorities.put(0, 1.0F);

      VkDeviceQueueCreateInfo.Buffer queueCis = VkDeviceQueueCreateInfo.calloc(1, stack);
      queueCis.get(0)
            .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
            .queueFamilyIndex(this.graphicsQueueFamily)
            .pQueuePriorities(pPriorities);

      VkDeviceCreateInfo deviceCi = VkDeviceCreateInfo.calloc(stack);
      deviceCi.sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
      deviceCi.pQueueCreateInfos(queueCis);
      PointerBuffer devExt = this.collectDeviceExtensions(stack);
      if (devExt != null) {
         deviceCi.ppEnabledExtensionNames(devExt);
      }

      PointerBuffer pDevice = stack.mallocPointer(1);
      int err = VK10.vkCreateDevice(this.physicalDevice, deviceCi, null, pDevice);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateDevice failed: " + vulkanError(err));
      }
      this.device = new VkDevice(pDevice.get(0), this.physicalDevice, deviceCi);

      PointerBuffer pQueue = stack.mallocPointer(1);
      VK10.vkGetDeviceQueue(this.device, this.graphicsQueueFamily, 0, pQueue);
      this.graphicsQueue = new VkQueue(pQueue.get(0), this.device);
      System.out.println("[Zero/Vulkan] vkCreateDevice OK, graphics queue acquired");
   }

   // Собирает device-расширения для zero-copy VK↔GL interop. Включаются только те, что
   // реально поддержаны GPU (см. detectInteropExtensions) — иначе vkCreateDevice упал бы
   // с VK_ERROR_EXTENSION_NOT_PRESENT и Vulkan ушёл бы в GL-fallback.
   private PointerBuffer collectDeviceExtensions(MemoryStack stack) {
      java.util.List<String> ext = new java.util.ArrayList<>();
      if (this.externalMemoryWin32) {
         ext.add(org.lwjgl.vulkan.KHRExternalMemory.VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME);
         ext.add(org.lwjgl.vulkan.KHRExternalMemoryWin32.VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME);
      }
      if (this.externalSemaphoreWin32) {
         ext.add(org.lwjgl.vulkan.KHRExternalSemaphore.VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME);
         ext.add(org.lwjgl.vulkan.KHRExternalSemaphoreWin32.VK_KHR_EXTERNAL_SEMAPHORE_WIN32_EXTENSION_NAME);
      }
      if (ext.isEmpty()) {
         return null;
      }
      PointerBuffer buffer = stack.mallocPointer(ext.size());
      for (String name : ext) {
         buffer.put(stack.UTF8(name));
      }
      buffer.flip();
      return buffer;
   }

   private void createCommandPool(MemoryStack stack) {
      VkCommandPoolCreateInfo ci = VkCommandPoolCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
      ci.queueFamilyIndex(this.graphicsQueueFamily);
      ci.flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

      LongBuffer pPool = stack.mallocLong(1);
      int err = VK10.vkCreateCommandPool(this.device, ci, null, pPool);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateCommandPool failed: " + vulkanError(err));
      }
      this.commandPoolHandle = pPool.get(0);
      System.out.println("[Zero/Vulkan] vkCreateCommandPool OK (handle=" + this.commandPoolHandle + ")");
   }

   public void destroy() {
      if (!this.created) {
         return;
      }
      if (this.commandPoolHandle != 0 && this.device != null) {
         VK10.vkDestroyCommandPool(this.device, this.commandPoolHandle, null);
         this.commandPoolHandle = 0;
      }
      if (this.physicalDeviceProperties != null) {
         this.physicalDeviceProperties.free();
         this.physicalDeviceProperties = null;
      }
      if (this.memoryProperties != null) {
         this.memoryProperties.free();
         this.memoryProperties = null;
      }
      if (this.device != null) {
         VK10.vkDestroyDevice(this.device, null);
         this.device = null;
      }
      if (this.instance != null) {
         VK10.vkDestroyInstance(this.instance, null);
         this.instance = null;
      }
      this.created = false;
      System.out.println("[Zero/Vulkan] Context destroyed");
   }

   public static String vulkanError(int err) {
      return switch (err) {
         case VK10.VK_ERROR_OUT_OF_HOST_MEMORY -> "VK_ERROR_OUT_OF_HOST_MEMORY";
         case VK10.VK_ERROR_OUT_OF_DEVICE_MEMORY -> "VK_ERROR_OUT_OF_DEVICE_MEMORY";
         case VK10.VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED";
         case VK10.VK_ERROR_LAYER_NOT_PRESENT -> "VK_ERROR_LAYER_NOT_PRESENT";
         case VK10.VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT";
         case VK10.VK_ERROR_INCOMPATIBLE_DRIVER -> "VK_ERROR_INCOMPATIBLE_DRIVER";
         default -> "VK code " + err;
      };
   }
}
