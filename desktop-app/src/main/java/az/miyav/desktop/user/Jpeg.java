package az.miyav.desktop.user;

import az.miyav.common.error.ValidationException;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/** Turns a picked image file into a JPEG under the storage limit, the way the phone app does. */
final class Jpeg {
    static final int LISTING_BYTES = 2 * 1024 * 1024;
    static final int CHAT_BYTES = 5 * 1024 * 1024;

    private Jpeg() {
    }

    static File pick(Window owner, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(title, "*.jpg", "*.jpeg", "*.png", "*.webp", "*.bmp"));
        return chooser.showOpenDialog(owner);
    }

    static byte[] encode(File file, int maxEdge, int maxBytes) {
        BufferedImage source;
        try {
            source = ImageIO.read(file);
        } catch (IOException exception) {
            throw new ValidationException("chat.photo");
        }
        if (source == null) {
            throw new ValidationException("chat.photo");
        }
        double ratio = Math.min(1.0, maxEdge / (double) Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        for (float quality = 0.88f; quality >= 0.5f; quality -= 0.08f) {
            byte[] bytes = write(rgb, quality);
            if (bytes.length <= maxBytes) {
                return bytes;
            }
        }
        throw new ValidationException("media.too_large");
    }

    private static byte[] write(BufferedImage image, float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException exception) {
            throw new ValidationException("chat.photo");
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }
}
