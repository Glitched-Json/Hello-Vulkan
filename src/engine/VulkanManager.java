package engine;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.vulkan.VK10.*;

public final class VulkanManager {
    private static boolean initialized = false;
    private static VkInstance instance;

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        createInstance();

        DataManager.initializeMessage("Vulkan Instance");

        if (DataManager.getFlag("show_instance_extensions"))
            printExtensions();
    }

    private static void createInstance() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo applicationInfo = VkApplicationInfo
                    .create()
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(getSetting("game name"))
                    .applicationVersion(DataManager.getVulkanVersion("game version"))
                    .pEngineName(getSetting("engine name"))
                    .engineVersion(DataManager.getVulkanVersion("engine version"))
                    .apiVersion(VK_API_VERSION_1_0);

            PointerBuffer requiredExtensions = glfwGetRequiredInstanceExtensions();
            if (requiredExtensions == null)
                throw new IllegalStateException("No required Vulkan extensions found.");
            VkInstanceCreateInfo instanceCreateInfo = VkInstanceCreateInfo
                    .create()
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(applicationInfo)
                    .ppEnabledExtensionNames(requiredExtensions);

            PointerBuffer handle = stack.mallocPointer(1);
            int statusCode = vkCreateInstance(instanceCreateInfo, null, handle);
            if (statusCode != VK_SUCCESS)
                throw new RuntimeException("Failed to create Vulkan Instance: " + statusCode);

            instance = new VkInstance(handle.get(0), instanceCreateInfo);
        }
    }

    private static void printExtensions() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer extensionsCount = stack.mallocInt(1);
            vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionsCount, null);
            int count = extensionsCount.get(0);

            VkExtensionProperties.Buffer extensions = VkExtensionProperties.malloc(count, stack);

            vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionsCount, extensions);

            System.out.println("[INSTANCE EXTENSIONS]: ");
            for (int i = 0; i < extensions.capacity(); i++) {
                VkExtensionProperties extension = extensions.get(i);
                System.out.println("\t" + extension.extensionNameString());
            }
        }
    }

    private static ByteBuffer getSetting(String setting) {
        return StandardCharsets.UTF_8.encode(DataManager.getSettingString(setting) + '\0');
    }

    public static void cleanup() {
        if (!initialized || Main.isRunning()) return;
        initialized = false;

        vkDestroyInstance(instance, null);

        DataManager.cleanupMessage("Vulkan Instance");
    }
}
