package co.mysticmc.items.capture;

import net.minecraft.client.Minecraft;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

final class ItemCaptureFiles {
    private static final DateTimeFormatter FOLDER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS");

    private ItemCaptureFiles() {}

    record Frame(BufferedImage image, int tick) {}

    record SavedCapture(Path folder, Path imagePath, String extension) {}

    static SavedCapture save(Minecraft client, String itemId, String json, List<Frame> frames,
                     int loopTicks, boolean imageOnly) throws IOException {
        if (frames.isEmpty()) throw new IOException("No image frame was captured");
        Path root = client.gameDirectory.toPath().resolve(imageOnly
                ? "screenshots" : "screenshots/mystic-item-browser-item-queue");
        Files.createDirectories(root);
        String safeId = itemId.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeId.length() > 60) safeId = safeId.substring(0, 60);
        String basename = FOLDER_TIME.format(LocalDateTime.now()) + "_" + safeId + "_"
                + UUID.randomUUID().toString().substring(0, 8);
        Path folder = imageOnly ? root : root.resolve(basename);
        if (!imageOnly) Files.createDirectory(folder);

        String extension = loopTicks > 0 ? ".gif" : ".png";
        Path imagePath = folder.resolve((imageOnly ? basename : "preview") + extension);
        try {
            if (loopTicks > 0) {
                writeGif(imagePath, frames, loopTicks);
            } else if (!ImageIO.write(frames.getFirst().image(), "png", imagePath.toFile())) {
                throw new IOException("No PNG writer is available");
            }
            if (!imageOnly) Files.writeString(folder.resolve("item.json"), json, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(imagePath);
            if (!imageOnly) {
                Files.deleteIfExists(folder.resolve("item.json"));
                Files.deleteIfExists(folder);
            }
            throw e;
        }
        return new SavedCapture(folder, imagePath, extension);
    }

    private static void writeGif(Path path, List<Frame> frames, int loopTicks) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName("gif");
        if (!writers.hasNext()) throw new IOException("No GIF writer is available");
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(path.toFile())) {
            if (output == null) throw new IOException("Could not open GIF output");
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frames.size(); i++) {
                Frame frame = frames.get(i);
                int nextTick = i + 1 < frames.size() ? frames.get(i + 1).tick() : loopTicks;
                int durationTicks = nextTick - frame.tick() + (i == 0 ? frame.tick() : 0);
                int delay = Math.max(1, durationTicks) * 5;
                IIOMetadata metadata = writer.getDefaultImageMetadata(
                        ImageTypeSpecifier.createFromRenderedImage(frame.image()), null);
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode control = child(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", Integer.toString(delay));
                control.setAttribute("transparentColorIndex", "0");
                if (i == 0) {
                    IIOMetadataNode extensions = child(root, "ApplicationExtensions");
                    IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[] { 1, 0, 0 });
                    extensions.appendChild(loop);
                }
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame.image(), null, metadata), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
        for (int i = 0; i < parent.getLength(); i++) {
            if (parent.item(i) instanceof IIOMetadataNode node && name.equals(node.getNodeName())) return node;
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        parent.appendChild(node);
        return node;
    }
}
