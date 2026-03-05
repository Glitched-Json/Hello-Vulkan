package engine;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public final class VulkanManager {
    private static boolean initialized = false;

    private static VkInstance instance;
    private static VkPhysicalDevice physicalDevice;
    private static VkDevice logicalDevice;
    private static VkQueue graphicsQueue;
    private static VkQueue presentQueue;
    private static QueueFamilyIndices queueFamilyIndices;
    private static long surfaceID;
    private static long swapchainID;
    private static VkExtent2D swapChainExtent;
    private static int swapChainFormat;

    private static VkLayerProperties.Buffer layerProperties;
    private static VkExtensionProperties.Buffer extensionProperties;
    private static final List<Long> swapChainImages = new ArrayList<>();
    private static final List<Long> swapChainImageViews = new ArrayList<>();

    private static String extensionInfo, layerInfo, deviceInfo, propertiesInfo = "";

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        createInstance();
        createSurface();
        pickPhysicalDevice();
        createLogicalDevice();
        createSwapChain();
        createImageViews();

        DataManager.initializeMessage("Vulkan Instance");

        if (DataManager.getInfoFlag("show_instance_extensions")) System.out.println(extensionInfo);
        if (DataManager.getInfoFlag("show_instance_layers")) System.out.println(layerInfo);
        if (DataManager.getInfoFlag("show_devices")) System.out.println(deviceInfo);
        if (DataManager.getInfoFlag("show_device_properties")) System.out.print(propertiesInfo);
    }

    private static void createInstance() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Layer Properties ----------------------------------------------------------------------------------------
            IntBuffer layerCount = stack.callocInt(1);
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
            IntBuffer extensionsCount = stack.callocInt(1);
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
                    .calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(getSetting("game name"))
                    .applicationVersion(DataManager.getVulkanVersion("game version"))
                    .pEngineName(getSetting("engine name"))
                    .engineVersion(DataManager.getVulkanVersion("engine version"))
                    .apiVersion(VK_API_VERSION_1_0);

            // Instance Create Info ------------------------------------------------------------------------------------
            VkInstanceCreateInfo instanceCreateInfo = VkInstanceCreateInfo
                    .calloc(stack)
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

    private static void createSurface() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer surfaceHandle = stack.mallocLong(1);
            long statusCode = GLFWVulkan.glfwCreateWindowSurface(instance, Window.getId(), null, surfaceHandle);
            if (statusCode != VK_SUCCESS)
                throw new IllegalStateException("Failed to create Window Surface.");
            surfaceID = surfaceHandle.get(0);
        }
    }

    private static void pickPhysicalDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer deviceNumber = stack.callocInt(1);
            vkEnumeratePhysicalDevices(instance, deviceNumber, null);
            if (deviceNumber.get(0) == 0)
                throw new IllegalStateException("Failed to find any GPUs with Vulkan Support.");

            PointerBuffer devicePointers = stack.mallocPointer(deviceNumber.get(0));
            vkEnumeratePhysicalDevices(instance, deviceNumber, devicePointers);

            VkPhysicalDevice[] devices = IntStream
                    .range(0, deviceNumber.get(0))
                    .mapToObj(i -> new VkPhysicalDevice(devicePointers.get(i), instance))
                    .toArray(VkPhysicalDevice[]::new);

            String[] deviceNames = Arrays.stream(devices)
                    .map(d -> getPhysicalDeviceProperties(d, stack).deviceNameString())
                    .toArray(String[]::new);

            deviceInfo = "[INFO]: -- DEVICES -- \n\t%s".formatted(String.join("\n\t", deviceNames));

            physicalDevice = null;
            int i = 0;
            for (VkPhysicalDevice d: devices) if (isDeviceSuitable(d, deviceNames[i++])) {
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
        IntBuffer queueIndex = stack.callocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueIndex, null);
        return queueIndex.get(0);
    }

    private static VkQueueFamilyProperties.Buffer getPhysicalDeviceQueueFamilyProperties(VkPhysicalDevice device, MemoryStack stack) {
        IntBuffer queueIndex = stack.callocInt(1);
        VkQueueFamilyProperties.Buffer familyProperties = VkQueueFamilyProperties.calloc(getPhysicalDeviceQueueFamilySize(device, stack), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueIndex, familyProperties);
        return familyProperties;
    }

    private static void createLogicalDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (!queueFamilyIndices.isComplete())
                throw new IllegalStateException("No Device supporting QUEUE_GRAPHICS and SURFACE_SUPPORT was found.");

            // Queue Create Info
            Integer[] indices = queueFamilyIndices.getUniqueIndicesArray();
            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(indices.length, stack);
            for (int i=0; i<indices.length; i++)
                queueCreateInfos.get(i)
                        .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                        .queueFamilyIndex(indices[i])
                        .pQueuePriorities(stack.floats(1f));

            // Physical Device Features
            VkPhysicalDeviceFeatures features = getPhysicalDeviceFeatures(physicalDevice, stack);

            // Logical Device Create Info
            List<String> enabledExtensions = DataManager.getSettingList("device properties");
            PointerBuffer extensionNames = stack.mallocPointer(enabledExtensions.size());
            for (String extension: enabledExtensions) extensionNames.put(stack.UTF8(extension));
            extensionNames.flip();

            VkDeviceCreateInfo logicalDeviceCreateInfo = VkDeviceCreateInfo
                    .calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .pQueueCreateInfos(queueCreateInfos)
                    .pEnabledFeatures(features)
                    .ppEnabledExtensionNames(extensionNames);

            if (DataManager.getFlag("enable_validation_layers"))
                logicalDeviceCreateInfo.ppEnabledLayerNames(stack.pointers(stack.UTF8("VK_LAYER_KHRONOS_validation")));

            // Create Logical Device
            PointerBuffer handle = stack.mallocPointer(1);
            int statusCode = vkCreateDevice(physicalDevice, logicalDeviceCreateInfo, null, handle);
            if (statusCode != VK_SUCCESS)
                throw new RuntimeException("Failed to create Logical Device: " + statusCode);
            logicalDevice = new VkDevice(handle.get(0), physicalDevice, logicalDeviceCreateInfo);

            // Graphics Queue
            PointerBuffer graphicsHandle = stack.mallocPointer(1);
            vkGetDeviceQueue(logicalDevice, queueFamilyIndices.graphicsFamily, 0, graphicsHandle);
            graphicsQueue = new VkQueue(graphicsHandle.get(0), logicalDevice);

            // Present Queue
            PointerBuffer presentHandle = stack.mallocPointer(1);
            vkGetDeviceQueue(logicalDevice, queueFamilyIndices.presentFamily, 0, presentHandle);
            presentQueue = new VkQueue(presentHandle.get(0), logicalDevice);
        }
    }

    private static boolean isDeviceSuitable(VkPhysicalDevice device, String deviceName) {
        boolean extensionsSupported;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer extensionCount = stack.callocInt(1);
            vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCount, null);

            VkExtensionProperties.Buffer data = VkExtensionProperties.calloc(extensionCount.get(0), stack);
            vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCount, data);

            List<String> requiredExtensions = DataManager.getSettingList("device properties");
            List<String> supportedExtensions = data.stream().map(VkExtensionProperties::extensionNameString).toList();

            requiredExtensions.removeAll(supportedExtensions);

            extensionsSupported = requiredExtensions.isEmpty();

            propertiesInfo += "[INFO]: -- DEVICE PROPERTIES -- %s \n\t%s\n".formatted(
                    deviceName,
                    String.join("\n\t", supportedExtensions)
            );
        }

        return extensionsSupported
                && (queueFamilyIndices = getQueueFamilyIndices(device)).isComplete()
                && getSwapChainSupportDetails(device).isComplete();
    }

    private static QueueFamilyIndices getQueueFamilyIndices(VkPhysicalDevice device) {
        QueueFamilyIndices indices = new QueueFamilyIndices();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer sizeHandle = stack.callocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(device, sizeHandle, null);

            VkQueueFamilyProperties.Buffer familyProperties = VkQueueFamilyProperties.calloc(sizeHandle.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(device, sizeHandle, familyProperties);

            IntBuffer surfaceHandle = stack.callocInt(1);

            for (int i = 0; i < sizeHandle.get(0); i++) {
                KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surfaceID, surfaceHandle);
                if (surfaceHandle.get(0) == VK_TRUE) indices.presentFamily = i;

                if ((familyProperties.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0)
                    indices.graphicsFamily = i;

                if (indices.isComplete()) break;
            }
        }
        return indices;
    }

    private static void createSwapChain() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Create Info Arguments
            SwapChainSupportDetails details = getSwapChainSupportDetails(physicalDevice);
            VkSurfaceFormatKHR surfaceFormat = details.chooseSwapChainSurfaceFormat();

            int imageCount = details.capabilities.minImageCount() + (int) Math.max(DataManager.getSetting("additional_swap_chain_images"), 0);
            if (details.capabilities.maxImageCount() != 0)
                imageCount = Math.min(imageCount, details.capabilities.maxImageCount());

            QueueFamilyIndices indices = getQueueFamilyIndices(physicalDevice);
            boolean isExclusive = indices.graphicsFamily.equals(indices.presentFamily);

            // Create Info
            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR
                    .calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                    .surface(surfaceID)
                    .minImageCount(imageCount)
                    .imageFormat(swapChainFormat = surfaceFormat.format())
                    .imageColorSpace(surfaceFormat.colorSpace())
                    .imageExtent(swapChainExtent = details.chooseSwapChainExtent())
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .imageSharingMode(isExclusive ? VK_SHARING_MODE_EXCLUSIVE : VK_SHARING_MODE_CONCURRENT)
                    .queueFamilyIndexCount(isExclusive ? 0 : 2)
                    .pQueueFamilyIndices(isExclusive ? stack.ints() : stack.ints(indices.graphicsFamily, indices.presentFamily))
                    .preTransform(details.capabilities.currentTransform())
                    .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(details.chooseSwapChainPresentMode())
                    .clipped(true)
                    .oldSwapchain(VK_NULL_HANDLE);

            // Create SwapChain
            LongBuffer handle = stack.callocLong(1);
            int statusCode = vkCreateSwapchainKHR(logicalDevice, createInfo, null, handle);
            if (statusCode != VK_SUCCESS)
                throw new IllegalStateException("Failed to create Swap Chain: " + statusCode);
            swapchainID = handle.get(0);

            // Retrieve Handles
            IntBuffer count = stack.callocInt(1);
            vkGetSwapchainImagesKHR(logicalDevice, swapchainID, count, null);

            LongBuffer images = stack.callocLong(count.get(0));
            vkGetSwapchainImagesKHR(logicalDevice, swapchainID, count, images);

            for (int i=0; i<count.get(0); i++)
                swapChainImages.add(images.get(i));
        }
    }

    private static SwapChainSupportDetails getSwapChainSupportDetails(VkPhysicalDevice device) {
        SwapChainSupportDetails details = new SwapChainSupportDetails();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Surface Capabilities
            details.capabilities = VkSurfaceCapabilitiesKHR.create();
            KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surfaceID, details.capabilities);

            // Surface Formats
            IntBuffer formatCount = stack.callocInt(1);
            KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(device, surfaceID, formatCount, null);
            details.surfaceFormatsSize = formatCount.get(0);

            if (details.surfaceFormatsSize != 0) {
                details.surfaceFormats = VkSurfaceFormatKHR.create(details.surfaceFormatsSize);
                KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(device, surfaceID, formatCount, details.surfaceFormats);
            }

            // Presentation Modes
            // int[] presentationCount = new int[1];
            IntBuffer presentationCount = stack.callocInt(1);
            KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(device, surfaceID, presentationCount, null);

            if (presentationCount.get(0) != 0) {
                details.presentModes = new int[presentationCount.get(0)];
                KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(device, surfaceID, new int[1], details.presentModes);
            }
        }

        return details;
    }

    private static void createImageViews() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (long imageID: swapChainImages) {
                VkImageViewCreateInfo createInfo = VkImageViewCreateInfo
                        .create()
                        .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                        .image(imageID)
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(swapChainFormat)
                        .components(VkComponentMapping.create().set(
                                VK_COMPONENT_SWIZZLE_IDENTITY,
                                VK_COMPONENT_SWIZZLE_IDENTITY,
                                VK_COMPONENT_SWIZZLE_IDENTITY,
                                VK_COMPONENT_SWIZZLE_IDENTITY))
                        .subresourceRange(VkImageSubresourceRange.create().set(
                                VK_IMAGE_ASPECT_COLOR_BIT,
                                0,
                                1,
                                0,
                                1
                        ));

                LongBuffer handle = stack.callocLong(1);
                int statusCode = vkCreateImageView(logicalDevice, createInfo, null, handle);
                if (statusCode != VK_SUCCESS)
                    throw new IllegalStateException("Failed to create Image View: " + statusCode);
                swapChainImageViews.add(handle.get(0));
            }
        }
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

        for (Long imageViewID: swapChainImageViews)
            vkDestroyImageView(logicalDevice, imageViewID, null);
        vkDestroySwapchainKHR(logicalDevice, swapchainID, null);
        vkDestroyDevice(logicalDevice, null);
        KHRSurface.vkDestroySurfaceKHR(instance, surfaceID, null);
        vkDestroyInstance(instance, null);

        DataManager.cleanupMessage("Vulkan Instance");
    }

    private static class QueueFamilyIndices {
        private Integer graphicsFamily = null;
        private Integer presentFamily = null;

        public boolean isComplete() { return graphicsFamily != null && presentFamily != null; }
        public Integer[] getUniqueIndicesArray() {
            return Arrays.stream(new Integer[]{graphicsFamily, presentFamily})
                    .distinct()
                    .filter(Objects::nonNull)
                    .toArray(Integer[]::new);
        }
    }

    private static class SwapChainSupportDetails {
        VkSurfaceCapabilitiesKHR capabilities;
        VkSurfaceFormatKHR.Buffer surfaceFormats;
        int surfaceFormatsSize = 0;
        int[] presentModes;

        public boolean isComplete() { return surfaceFormatsSize > 0 && presentModes.length > 0; }

        public VkSurfaceFormatKHR chooseSwapChainSurfaceFormat() {
            for (int i=0; i<surfaceFormatsSize; i++) {
                VkSurfaceFormatKHR format = surfaceFormats.get(i);
                if (format.format() == VK_FORMAT_B8G8R8A8_SRGB
                        && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
                    return format;
            }
            return surfaceFormats.get(0);
        }

        public int chooseSwapChainPresentMode() {
            int[] presentModePreferanceOrder = DataManager.getSettingList("present mode preferance").stream()
                    .mapToInt(s -> switch (s) {
                        case "IMMEDIATE" -> VK_PRESENT_MODE_IMMEDIATE_KHR;
                        case "FIFO" -> VK_PRESENT_MODE_FIFO_KHR;
                        case "FIFO_RELAXED" -> VK_PRESENT_MODE_FIFO_RELAXED_KHR;
                        case "MAILBOX" -> VK_PRESENT_MODE_MAILBOX_KHR;
                        default -> -1;
                    }).filter(i -> i >= 0)
                    .toArray();

            for (int preferance: presentModePreferanceOrder) for (int mode: presentModes)
                if (mode == preferance) return mode;

            return VK_PRESENT_MODE_FIFO_KHR;
        }

        public VkExtent2D chooseSwapChainExtent() {
            if (capabilities.currentExtent().width() != Integer.MAX_VALUE)
                return capabilities.currentExtent();

            int width, height;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer widthHandle = stack.callocInt(1);
                IntBuffer heightHandle = stack.callocInt(1);
                glfwGetFramebufferSize(Window.getId(), widthHandle, heightHandle);
                width = widthHandle.get(0);
                height = heightHandle.get(0);
            }

            return VkExtent2D.create()
                    .width( (int) Logic.clamp(width,  capabilities.minImageExtent().width(),  capabilities.maxImageExtent().width()))
                    .height((int) Logic.clamp(height, capabilities.minImageExtent().height(), capabilities.maxImageExtent().height()));
        }
    }
}
