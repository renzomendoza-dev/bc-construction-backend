package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.*;
import com.bcconstructionservices.inventory.exception.ValidationErrorResponse;
import com.bcconstructionservices.inventory.service.FileStorageService;
import com.bcconstructionservices.inventory.service.ItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST endpoints for managing the item/product catalog and item images.
 */
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
@Tag(name = "Items", description = "Manage the item/product catalog and item images")
public class ItemController {

    private final ItemService itemService;
    private final FileStorageService fileStorageService;

    @PostMapping
    @Operation(
            summary = "Create a new item",
            description = "Creates a new catalog item. Fails with 409 if the SKU is already in use by another item."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Item created",
                    content = @Content(schema = @Schema(implementation = ItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "An item with this SKU already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ItemResponse> createItem(@Valid @RequestBody ItemCreateRequest request) {
        ItemResponse response = itemService.createItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{itemId}")
    @Operation(
            summary = "Update an existing item",
            description = "Updates one or more fields of an existing item. Only non-null fields present in the "
                    + "request body are applied; omitted fields are left unchanged."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item updated",
                    content = @Content(schema = @Schema(implementation = ItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Item not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Another item already uses the requested SKU",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ItemResponse> updateItem(
            @Parameter(description = "Identifier of the item to update", example = "1")
            @PathVariable Long itemId,
            @Valid @RequestBody ItemUpdateRequest request) {
        return ResponseEntity.ok(itemService.updateItem(itemId, request));
    }

    @GetMapping("/{itemId}")
    @Operation(
            summary = "Get an item by id",
            description = "Retrieves a single item, including its images, by its identifier."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item found",
                    content = @Content(schema = @Schema(implementation = ItemResponse.class))),
            @ApiResponse(responseCode = "404", description = "Item not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ItemResponse> getItemById(
            @Parameter(description = "Identifier of the item to retrieve", example = "1")
            @PathVariable Long itemId) {
        return ResponseEntity.ok(itemService.getItemById(itemId));
    }

    @GetMapping("/sku/{sku}")
    @Operation(
            summary = "Get an item by SKU",
            description = "Retrieves a single item, including its images, by its unique SKU code."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item found",
                    content = @Content(schema = @Schema(implementation = ItemResponse.class))),
            @ApiResponse(responseCode = "404", description = "Item not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ItemResponse> getItemBySku(
            @Parameter(description = "SKU of the item to retrieve", example = "SKU-12345")
            @PathVariable String sku) {
        return ResponseEntity.ok(itemService.getItemBySku(sku));
    }

    @GetMapping
    @Operation(
            summary = "List items",
            description = "Returns a paged, optionally filtered list of items. All filters are optional and are "
                    + "combined with AND when more than one is supplied."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of items",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<ItemSummaryResponse>> listItems(
            @Parameter(description = "Filter by exact category match", example = "Electronics")
            @RequestParam(required = false) String category,
            @Parameter(description = "Filter by active status", example = "true")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Case-insensitive partial match against item name or SKU", example = "mouse")
            @RequestParam(required = false) String search,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(itemService.listItems(category, active, search, pageable));
    }

    @PatchMapping("/{itemId}/deactivate")
    @Operation(
            summary = "Deactivate an item",
            description = "Soft-disables an item by setting active to false. The item and its history are "
                    + "preserved, not deleted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Item deactivated"),
            @ApiResponse(responseCode = "404", description = "Item not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deactivateItem(
            @Parameter(description = "Identifier of the item to deactivate", example = "1")
            @PathVariable Long itemId) {
        itemService.deactivateItem(itemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{itemId}/images")
    @Operation(
            summary = "Add an image to an item",
            description = "Attaches a new image to an item. If sortOrder is omitted, the image is appended to the "
                    + "end of the item's existing image list."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Image added",
                    content = @Content(schema = @Schema(implementation = ItemImageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Item not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ItemImageResponse> addItemImage(
            @Parameter(description = "Identifier of the item to attach the image to", example = "1")
            @PathVariable Long itemId,
            @Valid @RequestBody ItemImageRequest request) {
        ItemImageResponse response = itemService.addItemImage(itemId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/images/{imageId}")
    @Operation(summary = "Remove an item image (deletes both the DB record and the stored file)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Image removed"),
            @ApiResponse(responseCode = "404", description = "Image not found")
    })
    public ResponseEntity<Void> removeItemImage(
            @Parameter(description = "Identifier of the image to remove", example = "42")
            @PathVariable Long imageId) {

        // The service deletes the DB row and returns the removed image (so we
        // can read its stored URL). It throws ResourceNotFoundException (-> 404)
        // if no such image exists.
        ItemImageResponse removed = itemService.removeItemImage(imageId);

        // ORDERING NOTE (important): the requirement text says delete the file
        // BEFORE the DB record. This implementation intentionally does the
        // opposite - DB row first (inside the service call above), file
        // second - because the failure modes are asymmetric:
        //
        //   - DB-delete-first, then file-delete fails  -> one orphaned file on
        //     disk. Harmless, recoverable, and deleteFile already logs + no-ops
        //     on a missing file so a later retry is safe.
        //
        //   - File-delete-first, then DB-delete fails  -> a surviving DB row
        //     pointing at a file that no longer exists -> broken image links in
        //     the app (404s when the URL is served). Worse and user-visible.
        //
        // Deleting the durable record first, then the reconstructable-or-
        // ignorable file, is the safer ordering. deleteFile never throws, so a
        // failure here won't turn a successful removal into an error response.
        // If you specifically need file-first semantics, fetch the image URL,
        // call fileStorageService.deleteFile(...), THEN call a void delete -
        // but be aware of the orphaned-row risk above.
        fileStorageService.deleteFile(removed.getImageUrl());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{itemId}/images/reorder")
    @Operation(
            summary = "Reorder an item's images",
            description = "Sets each listed image's sortOrder to match its position in orderedImageIds (index 0 "
                    + "becomes sortOrder 0, and so on). Images not included in the list keep their current sortOrder."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Images reordered",
                    content = @Content(schema = @Schema(implementation = ItemImageResponse.class))),
            @ApiResponse(responseCode = "404", description = "Item not found, or one of the image ids does not "
                    + "belong to this item",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ItemImageResponse>> reorderImages(
            @Parameter(description = "Identifier of the item whose images are being reordered", example = "1")
            @PathVariable Long itemId,
            @RequestBody List<Long> orderedImageIds) {
        return ResponseEntity.ok(itemService.reorderImages(itemId, orderedImageIds));
    }

    @Operation(summary = "Upload an image file for an item")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Image uploaded and linked to the item"),
            @ApiResponse(responseCode = "400", description = "Invalid file (empty, wrong type, or too large)"),
            @ApiResponse(responseCode = "404", description = "Item not found")
    })
    @PostMapping(
            value = "/{itemId}/images/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ItemImageResponse> uploadItemImage(
            @PathVariable Long itemId,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "sortOrder", defaultValue = "0") Integer sortOrder) {

        // 1. Validate + persist the file to disk, getting back the stored URL.
        //    FileStorageService throws InvalidFileException (-> 400) on an
        //    empty/oversized/wrong-type file before anything else happens.
        String imageUrl = fileStorageService.storeFile(image, "items");

        // 2. Build the ItemImageRequest internally from the storage result -
        //    the client never needs to know or supply the URL.
        ItemImageRequest request = new ItemImageRequest();
        request.setImageUrl(imageUrl);
        request.setSortOrder(sortOrder);

        // 3. Reuse the existing service method. It throws ResourceNotFoundException
        //    (-> 404) if itemId doesn't exist.
        //
        //    ORDERING NOTE: the file is written to disk BEFORE we know whether
        //    the item exists, so a 404 here leaves an orphaned file on disk.
        //    If that matters, either (a) verify item existence before storing
        //    (an extra service call), or (b) catch the exception here and call
        //    fileStorageService.deleteFile(imageUrl) before rethrowing, to
        //    compensate. Left as the simple path since a 404 on upload is an
        //    unusual client error, but flagging the tradeoff explicitly.
        ItemImageResponse response = itemService.addItemImage(itemId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


}