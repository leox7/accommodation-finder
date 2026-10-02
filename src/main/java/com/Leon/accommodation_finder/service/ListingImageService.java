package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.ListingImageResponseDto;
import com.Leon.accommodation_finder.exception.FileTooLargeException;
import com.Leon.accommodation_finder.exception.ImageLimitReachedException;
import com.Leon.accommodation_finder.exception.InvalidFileTypeException;
import com.Leon.accommodation_finder.exception.ListingImageNotFoundException;
import com.Leon.accommodation_finder.exception.PrimaryImageExistsException;
import com.Leon.accommodation_finder.model.Listing;
import com.Leon.accommodation_finder.model.ListingImage;
import com.Leon.accommodation_finder.repository.ListingImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

// Saves and deletes listing image files on disk and their rows in the database.
// Ownership is checked by ListingService before these methods are called.
@Service
public class ListingImageService {

    private static final Logger logger = LoggerFactory.getLogger(ListingImageService.class);

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB
    private static final int MAX_SECONDARY_IMAGES = 4;
    // must match the resource handler in WebConfig
    private static final String IMAGE_URL_PREFIX = "/uploads/listings/";

    private final ListingImageRepository listingImageRepository;
    private final Path uploadDir;

    @Autowired
    public ListingImageService(ListingImageRepository listingImageRepository,
                               @Value("${app.upload-dir}") String uploadDir) {
        this.listingImageRepository = listingImageRepository;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath();

        // create the upload folder on startup if it does not exist yet
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload folder " + this.uploadDir, e);
        }
    }

    public ListingImageResponseDto saveImage(Listing listing, MultipartFile file, boolean isPrimary) {
        try {
            String contentType = file.getContentType();
            if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
                throw new InvalidFileTypeException();
            }

            if (file.getSize() > MAX_FILE_SIZE) {
                throw new FileTooLargeException();
            }

            // the content type is sent by the client and can be faked (a .txt renamed to .jpg is
            // sent as image/jpeg), so also check the file really starts like a JPG or PNG
            byte[] bytes = file.getBytes();
            if (!hasImageSignature(bytes, contentType)) {
                throw new InvalidFileTypeException();
            }

            if (isPrimary && listingImageRepository.countImages(listing.getId(), true) > 0) {
                throw new PrimaryImageExistsException();
            }

            if (!isPrimary && listingImageRepository.countImages(listing.getId(), false) >= MAX_SECONDARY_IMAGES) {
                throw new ImageLimitReachedException(MAX_SECONDARY_IMAGES);
            }

            // never trust the original file name, the extension comes from the checked content type
            String extension = contentType.equals("image/png") ? ".png" : ".jpg";
            String fileName = UUID.randomUUID() + extension;
            Files.write(uploadDir.resolve(fileName), bytes);

            ListingImage image = new ListingImage();
            image.setListing(listing);
            image.setFileName(fileName);
            image.setPrimary(isPrimary);

            return toResponse(listingImageRepository.save(image));
        } catch (IOException e) {
            logger.error("Could not save image file for listing with id {}: {}", listing.getId(), e.getMessage());
            throw new RuntimeException("Could not save the image file", e);
        } catch (Exception e) {
            logger.error("Error while uploading image for listing with id {}: {}", listing.getId(), e.getMessage());
            throw e;
        }
    }

    public void deleteImage(Long listingId, Long imageId) {
        try {
            ListingImage image = listingImageRepository.findByIdAndListingId(imageId, listingId)
                    .orElseThrow(() -> new ListingImageNotFoundException(imageId));

            listingImageRepository.delete(image);
            deleteFile(image.getFileName());
        } catch (Exception e) {
            logger.error("Error while deleting image {} of listing with id {}: {}", imageId, listingId, e.getMessage());
            throw e;
        }
    }

    // used when a whole listing is deleted, so no image rows or files are left behind
    public void deleteAllImages(Long listingId) {
        try {
            List<ListingImage> images = listingImageRepository.findByListingIdOrderByIdAsc(listingId);
            listingImageRepository.deleteAll(images);
            for (ListingImage image : images) {
                deleteFile(image.getFileName());
            }
        } catch (Exception e) {
            logger.error("Error while deleting images of listing with id {}: {}", listingId, e.getMessage());
            throw e;
        }
    }

    public List<ListingImage> getImages(Long listingId) {
        return listingImageRepository.findByListingIdOrderByIdAsc(listingId);
    }

    public String getImageUrl(ListingImage image) {
        return IMAGE_URL_PREFIX + image.getFileName();
    }

    // JPG files start with the bytes FF D8 FF, PNG files start with 89 followed by the letters PNG
    private boolean hasImageSignature(byte[] bytes, String contentType) {
        if (contentType.equals("image/jpeg")) {
            return bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF;
        }
        return bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G';
    }

    // the database row is already gone, so a file that cannot be removed is only logged
    private void deleteFile(String fileName) {
        try {
            Files.deleteIfExists(uploadDir.resolve(fileName));
        } catch (IOException e) {
            logger.error("Could not delete image file {}: {}", fileName, e.getMessage());
        }
    }

    private ListingImageResponseDto toResponse(ListingImage image) {
        ListingImageResponseDto response = new ListingImageResponseDto();
        response.setId(image.getId());
        response.setUrl(getImageUrl(image));
        response.setIsPrimary(image.isPrimary());
        return response;
    }
}
