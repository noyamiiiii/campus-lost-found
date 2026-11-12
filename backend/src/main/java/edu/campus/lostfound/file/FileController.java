package edu.campus.lostfound.file;

import edu.campus.lostfound.auth.AuthenticatedUser;
import edu.campus.lostfound.common.ApiResponse;
import edu.campus.lostfound.file.FileDtos.FileView;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@Profile("local")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileView> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "location", required = false) String location,
            Authentication authentication
    ) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.ok(fileService.upload(user.userId(), file, kind, location));
    }
}