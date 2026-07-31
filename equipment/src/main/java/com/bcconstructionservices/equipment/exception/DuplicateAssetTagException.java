package com.bcconstructionservices.equipment.exception;

public class DuplicateAssetTagException extends RuntimeException {
    public DuplicateAssetTagException(String assetTag) {
        super("Equipment with asset tag '" + assetTag + "' already exists");
    }
}