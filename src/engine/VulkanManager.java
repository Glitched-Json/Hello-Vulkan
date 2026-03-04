package engine;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.vulkan.VK10.*;

public final class VulkanManager {
    private static boolean initialized = false;

    private static VkInstance instance;
    private static VkPhysicalDevice physicalDevice;
    private static VkDevice logicalDevice;
    private static VkQueue graphicsQueue;

    private static VkLayerProperties.Buffer layerProperties;
    private static VkExtensionProperties.Buffer extensionProperties;

    private static String extensionInfo, layerInfo, deviceInfo;

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        createInstance();
        pickPhysicalDevice();
        createLogicalDevice();

        DataManager.initializeMessage("Vulkan Instance");

        if (DataManager.getInfoFlag("show_instance_extensions")) System.out.println(extensionInfo);
        if (DataManager.getInfoFlag("show_instance_layers")) System.out.println(layerInfo);
        if (DataManager.getInfoFlag("show_devices")) System.out.println(deviceInfo);
    }

    private static void createInstance() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Layer Properties ----------------------------------------------------------------------------------------
            IntBuffer layerCount = stack.mallocInt(1);
            vkEnumerateInstanceLayerProperties(layerCount, null);

            layerProperties = VkLayerProperties.malloc(layerCount.get(0), stack);
            vkEnumerateInstanceLayerProperties(layerCount, layerProperties);

            if (DataManager.getFlag("enable_validation_layers") && checkValidationLayerSupport())
                DataManager.infoMessage("show_validation_check", "Validation Layer Support Check Complete");
            if (!DataManager.getFlag("enable_validation_layers"))
                DataManager.infoMessage("show_validation_check", "Validation Layer Support Check Disabled");

            layerInfo = "[INFO]: -- INSTANCE LAYERS -- \n\t%s"
                    .formatted(String.join("\n\t", layerProperties.stream().map(VkLayerProperties::layerNameString).toArray(String[]::new)));

            // Extension Properties ------------------------------------------------------------------------------------
            IntBuffer extensionsCount = stack.mallocInt(1);
            vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionsCount, null);

            extensionProperties = VkExtensionProperties.malloc(extensionsCount.get(0), stack);
            vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionsCount, extensionProperties);

            if (DataManager.getFlag("enable_extension_layers") && checkExtensionLayerSupport())
                DataManager.infoMessage("show_validation_check", "Extension Layer Support Check Complete");
            if (!DataManager.getFlag("enable_extension_layers"))
                DataManager.infoMessage("show_validation_check", "Extension Layer Support Check Disabled");

            extensionInfo = "[INFO]: -- INSTANCE EXTENSIONS -- \n\t%s".formatted(
                    String.join("\n\t", extensionProperties.stream().map(VkExtensionProperties::extensionNameString).toArray(String[]::new)));

            // Application Info ----------------------------------------------------------------------------------------
            VkApplicationInfo applicationInfo = VkApplicationInfo
                    .create()
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(getSetting("game name"))
                    .applicationVersion(DataManager.getVulkanVersion("game version"))
                    .pEngineName(getSetting("engine name"))
                    .engineVersion(DataManager.getVulkanVersion("engine version"))
                    .apiVersion(VK_API_VERSION_1_0);

            // Instance Create Info ------------------------------------------------------------------------------------
            VkInstanceCreateInfo instanceCreateInfo = VkInstanceCreateInfo
                    .create()
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(applicationInfo);

            PointerBuffer requiredExtensions = glfwGetRequiredInstanceExtensions();
            if (requiredExtensions == null)
                throw new IllegalStateException("No required Vulkan extensions found.");
            if (DataManager.getFlag("enable_extension_layers")) {
                List<String> extensionLayers = DataManager.getSettingList("extension layers");
                PointerBuffer includedExtensions = stack.mallocPointer(requiredExtensions.remaining() + extensionLayers.size());
                for (int i=0; i<requiredExtensions.remaining(); i++) includedExtensions.put(requiredExtensions.get(i));
                for (String extension: extensionLayers) includedExtensions.put(stack.UTF8(extension));
                includedExtensions.flip();
                instanceCreateInfo.ppEnabledExtensionNames(includedExtensions);
            } else instanceCreateInfo.ppEnabledExtensionNames(requiredExtensions);

            if (DataManager.getFlag("enable_validation_layers")) {
                List<String> validationLayers = DataManager.getSettingList("validation layers");
                PointerBuffer requiredLayers = stack.mallocPointer(validationLayers.size());
                for (String layer: validationLayers) requiredLayers.put(stack.UTF8(layer));
                requiredLayers.flip();
                instanceCreateInfo.ppEnabledLayerNames(requiredLayers);
            }

            // Vulkan Instance -----------------------------------------------------------------------------------------
            PointerBuffer handle = stack.mallocPointer(1);
            int statusCode = vkCreateInstance(instanceCreateInfo, null, handle);
            if (statusCode == VK_ERROR_INCOMPATIBLE_DRIVER)
                System.out.print("[VK_ERROR_INCOMPATIBLE_DRIVER] "); // TODO: implement MacOS fix [https://vulkan-tutorial.com/Drawing_a_triangle/Setup/Instance#page_Encountered-VK_ERROR_INCOMPATIBLE_DRIVER]
            if (statusCode != VK_SUCCESS)
                throw new RuntimeException("Failed to create Vulkan Instance: " + statusCode);

            instance = new VkInstance(handle.get(0), instanceCreateInfo);
        }
    }

    private static void pickPhysicalDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer deviceNumber = stack.mallocInt(1);
            vkEnumeratePhysicalDevices(instance, deviceNumber, null);
            if (deviceNumber.get(0) == 0)
                throw new IllegalStateException("Failed to find any GPUs with Vulkan Support.");

            PointerBuffer devicePointers = stack.mallocPointer(deviceNumber.get(0));
            vkEnumeratePhysicalDevices(instance, deviceNumber, devicePointers);

            VkPhysicalDevice[] devices = IntStream
                    .range(0, deviceNumber.get(0))
                    .mapToObj(i -> new VkPhysicalDevice(devicePointers.get(i), instance))
                    .toArray(VkPhysicalDevice[]::new);

            deviceInfo = "[INFO]: -- DEVICES -- \n\t%s".formatted(
                    String.join("\n\t", Arrays.stream(devices)
                            .map(d -> getPhysicalDeviceProperties(d, stack).deviceNameString())
                            .toArray(String[]::new)));

            physicalDevice = null;
            for (VkPhysicalDevice d: devices) if (isDeviceSuitable(d)) {
                physicalDevice = d;
                break;
            }
            if (physicalDevice == null) throw new IllegalStateException("Failed to find a suitable GPU.");
        }
    }

    private static VkPhysicalDeviceProperties getPhysicalDeviceProperties(VkPhysicalDevice device) {
        VkPhysicalDeviceProperties properties;
        try (MemoryStack stack = MemoryStack.stackPush()) { properties = getPhysicalDeviceProperties(device, stack); }
        return properties;
    }
    private static VkPhysicalDeviceProperties getPhysicalDeviceProperties(VkPhysicalDevice device, MemoryStack stack) {
        VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc(stack);
        vkGetPhysicalDeviceProperties(device, properties);
        return properties;
    }

    private static VkPhysicalDeviceFeatures getPhysicalDeviceFeatures(VkPhysicalDevice device) {
        VkPhysicalDeviceFeatures features;
        try (MemoryStack stack = MemoryStack.stackPush()) { features = getPhysicalDeviceFeatures(device, stack); }
        return features;
    }
    private static VkPhysicalDeviceFeatures getPhysicalDeviceFeatures(VkPhysicalDevice device, MemoryStack stack) {
        VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.malloc(stack);
        vkGetPhysicalDeviceFeatures(device, features);
        return features;
    }

    private static int getPhysicalDeviceQueueFamilySize(VkPhysicalDevice device) {
        int size;
        try (MemoryStack stack = MemoryStack.stackPush()) { size = getPhysicalDeviceQueueFamilySize(device, stack); }
        return size;
    }
    private static int getPhysicalDeviceQueueFamilySize(VkPhysicalDevice device, MemoryStack stack) {
        IntBuffer queueIndex = stack.mallocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueIndex, null);
        return queueIndex.get(0);
    }

    private static VkQueueFamilyProperties.Buffer getPhysicalDeviceQueueFamilyProperties(VkPhysicalDevice device) {
        VkQueueFamilyProperties.Buffer properties;
        try (MemoryStack stack = MemoryStack.stackPush()) { properties = getPhysicalDeviceQueueFamilyProperties(device, stack); }
        return properties;
    }
    private static VkQueueFamilyProperties.Buffer getPhysicalDeviceQueueFamilyProperties(VkPhysicalDevice device, MemoryStack stack) {
        IntBuffer queueIndex = stack.mallocInt(1);
        VkQueueFamilyProperties.Buffer familyProperties = VkQueueFamilyProperties.malloc(getPhysicalDeviceQueueFamilySize(device, stack), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueIndex, familyProperties);
        return familyProperties;
    }

    private static void createLogicalDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int graphicsFamilyIndex = getGraphicsIndex(physicalDevice).orElseThrow(() ->
                    new IllegalStateException("No Graphics Family supporting QUEUE_GRAPHICS was found."));

            // Queue Create Info
            VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo
                    .calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(graphicsFamilyIndex)
                    .pQueuePriorities(stack.floats(1));

            // Physical Device Features
            VkPhysicalDeviceFeatures features = getPhysicalDeviceFeatures(physicalDevice, stack);

            // Logical Device Create Info
            VkDeviceCreateInfo logicalDeviceCreateInfo = VkDeviceCreateInfo
                    .create()
                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .pQueueCreateInfos(queueCreateInfo)
                    .pEnabledFeatures(features);

            if (DataManager.getFlag("enable_validation_layers"))
                logicalDeviceCreateInfo.ppEnabledLayerNames(stack.pointers(stack.UTF8("VK_LAYER_KHRONOS_validation")));

            // Create Logical Device
            PointerBuffer handle = stack.mallocPointer(1);
            int statusCode = vkCreateDevice(physicalDevice, logicalDeviceCreateInfo, null, handle);
            if (statusCode != VK_SUCCESS)
                throw new RuntimeException("Failed to create Logical Device: " + statusCode);
            logicalDevice = new VkDevice(handle.get(0), physicalDevice, logicalDeviceCreateInfo);

            // Graphics Queue
            PointerBuffer queueHandle = stack.mallocPointer(1);
            vkGetDeviceQueue(logicalDevice, graphicsFamilyIndex, 0, queueHandle);
            graphicsQueue = new VkQueue(queueHandle.get(0), logicalDevice);
        }
    }

    private static boolean isDeviceSuitable(VkPhysicalDevice device) {
        return getGraphicsIndex(device).isPresent();
    }

    private static Optional<Integer> getGraphicsIndex(VkPhysicalDevice device) {
        for (int i = 0; i < getPhysicalDeviceQueueFamilySize(device); i++)
            if ((getPhysicalDeviceQueueFamilyProperties(device).get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0)
                return Optional.of(i);
        return Optional.empty();
    }

    private static boolean checkValidationLayerSupport() {
        List<String> layersNotFound = new ArrayList<>();
        List<String> availableLayers = layerProperties.stream().map(VkLayerProperties::layerNameString).toList();
        for (String requestedLayer: DataManager.getSettingList("validation layers"))
            if (!availableLayers.contains(requestedLayer)) layersNotFound.add(requestedLayer);
        if (!layersNotFound.isEmpty())
            throw new RuntimeException("Validation Layers Requested but Not Found: [%s]".formatted(
                    String.join(", ", layersNotFound.toArray(String[]::new))));
        return true;
    }

    private static boolean checkExtensionLayerSupport() {
        List<String> extensionsNotFound = new ArrayList<>();
        List<String> availableExtensions = extensionProperties.stream().map(VkExtensionProperties::extensionNameString).toList();
        for (String requestedLayer: DataManager.getSettingList("extension layers"))
            if (!availableExtensions.contains(requestedLayer)) extensionsNotFound.add(requestedLayer);
        if (!extensionsNotFound.isEmpty())
            throw new RuntimeException("Extension Layers Requested but Not Found: [%s]".formatted(
                    String.join(", ", extensionsNotFound.toArray(String[]::new))));
        return true;
    }

    private static ByteBuffer getSetting(String setting) {
        return StandardCharsets.UTF_8.encode(DataManager.getSettingString(setting) + '\0');
    }

    public static void cleanup() {
        if (!initialized || Main.isRunning()) return;
        initialized = false;

        vkDestroyDevice(logicalDevice, null);
        vkDestroyInstance(instance, null);

        DataManager.cleanupMessage("Vulkan Instance");
    }
}
