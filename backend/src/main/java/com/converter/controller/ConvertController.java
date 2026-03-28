package com.converter.controller;

import com.converter.dto.ConvertRequest;
import com.converter.dto.ConvertResponse;
import com.converter.service.ImageConvertService;
import com.converter.service.TextConvertService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/convert")
@CrossOrigin(origins = "*")
public class ConvertController {

    private final TextConvertService textConvertService;
    private final ImageConvertService imageConvertService;

    public ConvertController(TextConvertService textConvertService, ImageConvertService imageConvertService) {
        this.textConvertService = textConvertService;
        this.imageConvertService = imageConvertService;
    }

    @PostMapping("/text")
    public ResponseEntity<?> convertText(@RequestBody ConvertRequest request) {
        try {
            String output = textConvertService.convert(request.type(), request.input());
            return ResponseEntity.ok(new ConvertResponse(output));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/image")
    public ResponseEntity<byte[]> convertImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "jpeg") String format,
            @RequestParam(defaultValue = "0.92") float quality) {
        try {
            byte[] result = imageConvertService.convert(file.getBytes(), format, quality);
            String mimeType = "image/" + (format.equalsIgnoreCase("jpg") ? "jpeg" : format.toLowerCase());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .body(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
