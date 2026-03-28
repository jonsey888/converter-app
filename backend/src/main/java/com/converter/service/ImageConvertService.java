package com.converter.service;

import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

@Service
public class ImageConvertService {

    public byte[] convert(byte[] imageData, String targetFormat, float quality) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
        if (image == null) throw new IllegalArgumentException("Cannot decode image — unsupported format");

        String fmt = targetFormat.equalsIgnoreCase("jpg") ? "jpeg" : targetFormat.toLowerCase();
        String mimeType = "image/" + fmt;

        // JPEG does not support alpha channel
        if ("jpeg".equals(fmt) && image.getColorModel().hasAlpha()) {
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
            image = rgb;
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(mimeType);
        if (!writers.hasNext()) throw new IllegalArgumentException("No encoder available for format: " + targetFormat);

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(Math.max(0.0f, Math.min(1.0f, quality)));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
