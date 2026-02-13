package yan.goodshare.common;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.Iterator;
import java.util.stream.Stream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final Logger logger = LoggerFactory.getLogger(UploadController.class);

    private final Path fileStorageLocation;

    public UploadController() {
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    @PostConstruct
    public void init() {
        try (Stream<Path> paths = Files.walk(this.fileStorageLocation)) {
            paths.filter(Files::isRegularFile)
                 .forEach(path -> {
                     String filename = path.getFileName().toString();
                     if (isImage(filename) && !filename.contains("_thumb.")) {
                         String thumbName = getThumbFilename(filename);
                         Path thumbPath = this.fileStorageLocation.resolve(thumbName);
                         if (!Files.exists(thumbPath)) {
                             try {
                                 boolean generated = createThumbnail(path.toFile(), thumbPath.toFile());
                                 if (generated) {
                                     System.out.println("Generated missing thumbnail: " + thumbName);
                                 }
                             } catch (Exception e) {
                                 System.err.println("Failed to generate thumbnail for " + filename + ": " + e.getMessage());
                             }
                         }
                     }
                 });
        } catch (IOException e) {
            System.err.println("Failed to scan uploads directory: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        try {
            if(fileName.contains("..")) {
                throw new RuntimeException("Sorry! Filename contains invalid path sequence " + fileName);
            }

            String newFileName = UUID.randomUUID().toString() + "_" + fileName;
            Path targetLocation = this.fileStorageLocation.resolve(newFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "http://localhost:8080/uploads/" + newFileName;
            String thumbnailUrl = fileUrl;

            // Generate thumbnail if image
            if (isImage(newFileName)) {
                String thumbFileName = getThumbFilename(newFileName);
                Path thumbLocation = this.fileStorageLocation.resolve(thumbFileName);
                try {
                    boolean resized = createThumbnail(targetLocation.toFile(), thumbLocation.toFile());
                    if (resized) {
                        thumbnailUrl = "http://localhost:8080/uploads/" + thumbFileName;
                    }
                } catch (Exception e) {
                    System.err.println("Failed to generate thumbnail: " + e.getMessage());
                    thumbnailUrl = fileUrl;
                }
            }

            Map<String, String> response = new HashMap<>();
            response.put("url", fileUrl);
            response.put("thumbnailUrl", thumbnailUrl);
            
            return ResponseEntity.ok(response);
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body("Could not store file " + fileName + ". Please try again!");
        }
    }

    @DeleteMapping
    public ResponseEntity<?> deleteFile(@RequestParam("url") String fileUrl) {
        logger.info("Request to delete file with URL: {}", fileUrl);
        try {
            if (!fileUrl.contains("/uploads/")) {
                return ResponseEntity.badRequest().body("Invalid file URL");
            }
            
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            // Decode filename in case it is URL encoded (e.g. spaces, special chars)
            fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8.name());
            
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            logger.info("Attempting to delete file at: {}", filePath);
            
            // Security check: ensure file is within uploads directory
            if (!filePath.startsWith(this.fileStorageLocation)) {
                return ResponseEntity.badRequest().body("Invalid file path");
            }

            // Delete original file
            boolean deleted = Files.deleteIfExists(filePath);
            
            // Try to delete thumbnail if exists
            if (isImage(fileName)) {
                String thumbName = getThumbFilename(fileName);
                Path thumbPath = this.fileStorageLocation.resolve(thumbName);
                Files.deleteIfExists(thumbPath);
            }

            if (deleted) {
                logger.info("Successfully deleted file: {}", fileName);
                return ResponseEntity.ok().body("File deleted successfully");
            } else {
                logger.warn("File not found for deletion: {}", fileName);
                return ResponseEntity.status(404).body("File not found or already deleted");
            }
        } catch (IOException ex) {
            logger.error("Error deleting file: {}", ex.getMessage());
            return ResponseEntity.internalServerError().body("Could not delete file: " + ex.getMessage());
        }
    }

    private boolean isImage(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp");
    }

    private String getThumbFilename(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) return filename + "_thumb";
        return filename.substring(0, dotIndex) + "_thumb" + filename.substring(dotIndex);
    }

    private boolean createThumbnail(File originalFile, File thumbFile) throws IOException {
        BufferedImage originalImage = ImageIO.read(originalFile);
        if (originalImage == null) return false; // Not a valid image file

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        int maxDim = 800;
        long maxBytes = 50L * 1024L;
        if (originalFile.length() <= maxBytes) {
            return false;
        }
        if (originalWidth <= maxDim && originalHeight <= maxDim) {
            return false;
        }

        int newWidth, newHeight;
        if (originalWidth > originalHeight) {
            newWidth = maxDim;
            newHeight = (int) ((double) originalHeight / originalWidth * maxDim);
        } else {
            newHeight = maxDim;
            newWidth = (int) ((double) originalWidth / originalHeight * maxDim);
        }

        String extension = getExtension(thumbFile.getName());
        boolean isPng = "png".equalsIgnoreCase(extension);

        // Preserve transparency for PNG, use RGB for others to avoid color issues
        int type = isPng ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;

        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, type);
        Graphics2D g = resizedImage.createGraphics();
        
        // Improve quality with Bicubic interpolation and other hints
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        
        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();

        if ("jpg".equalsIgnoreCase(extension) || "jpeg".equalsIgnoreCase(extension)) {
             writeJpeg(resizedImage, thumbFile, 0.9f); // 90% quality
        } else {
             ImageIO.write(resizedImage, extension, thumbFile);
        }
        return true;
    }

    private void writeJpeg(BufferedImage image, File file, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IllegalStateException("No writers found");
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "jpg" : filename.substring(dotIndex + 1);
    }
}
