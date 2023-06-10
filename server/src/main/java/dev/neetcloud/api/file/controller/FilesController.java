package dev.neetcloud.api.file.controller;

import dev.neetcloud.api.dir.model.Dir;
import dev.neetcloud.api.dir.model.GraphNode;
import dev.neetcloud.api.dir.service.DirService;
import dev.neetcloud.api.file.model.Files;
import dev.neetcloud.api.fileStorage.service.FileStorageService;
import dev.neetcloud.api.file.repository.FilesRepository;
import dev.neetcloud.api.users.model.Users;
import dev.neetcloud.api.users.repository.UsersRepository;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("api/v1/files")
public class FilesController {

	private static final Logger logger = LoggerFactory.getLogger(FilesController.class);

	@Autowired
	private FileStorageService fileStorageService;
	@Autowired
	private FilesRepository filesRepository;
	@Autowired
	private UsersRepository usersRepository;

	@Autowired
	private DirService dirService;

	@PostMapping("/uploadFile")
	public ResponseEntity<Map<String, Object>> uploadFile(
			@RequestParam("") MultipartFile file,
			@RequestParam("dirId") String dirId) {
		Map<String, Object> responseMap = new HashMap<>();

		// get user
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Users currentUser = usersRepository.getIdByEmail(authentication.getName());
		// logger.info(" current user id: " + currentUser.getId());

		// check file name
		String fileName = fileStorageService.checkFileName(file);

		long currentDirId = Long.parseLong(dirId);

		if (fileName != null && currentUser != null) {

			// save file data in the repo
			Files currentFile = new Files(
					fileName,
					file.getContentType(),
					currentDirId,
					file.getSize(),
					currentUser.getId(),
					currentUser.getId());

			// save in database
			filesRepository.save(currentFile);

			// get index for file
			String currentFileId = currentFile.getId();

			// save file to disk
			fileName = fileStorageService.storeFile(file, Long.toString(currentUser.getId()), currentFileId);

			responseMap.put("fileName", fileName);
			responseMap.put("type", file.getContentType());
			responseMap.put("size", file.getSize());

			return ResponseEntity.ok(responseMap);

		}

		responseMap.put("error", true);
		responseMap.put("message", "Invalid operation");
		return ResponseEntity.status(500).body(responseMap);
	}

	@GetMapping("/ls")
	public ResponseEntity<Map<String, Object>> getDir() {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Map<String, Object> responseMap = new HashMap<>();
		Users currentUser = usersRepository.getIdByEmail(authentication.getName());

		if (currentUser != null && authentication != null) {

			// get files from repo
			Iterable<Files> files = filesRepository.findAllByCreatedUserId(currentUser.getId());
			// logger.info(files.toString());
			GraphNode<Dir> dirTree = (GraphNode<Dir>) dirService.getDirTree(currentUser.getId());
			logger.info(dirTree.toString());


			responseMap.put("error", false);
			responseMap.put("email", authentication.getName().toString());
			responseMap.put("message", "Files were found");
			responseMap.put("files", files);
			responseMap.put("dirs", dirTree);

			return ResponseEntity.ok(responseMap);

		} else {
			responseMap.put("error", true);
			responseMap.put("message", "Invalid name");
			return ResponseEntity.status(401).body(responseMap);
		}
	}

	@PostMapping("/mv")
	public ResponseEntity<Map<String, Object>> moveFile(
			@RequestBody Map<String, String> body) 
		{
		
		logger.info("File Moving");
		Map<String, Object> responseMap = new HashMap<>();

		// get user
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Users currentUser = usersRepository.getIdByEmail(authentication.getName());

		// check file name
		String fileId = body.get("fileId");
		String targetDirId = body.get("targetDirId");
		String currentDirId = body.get("sourceDirId");

		// convert to long
		long targetDir = Long.parseLong(targetDirId);
		long currentDir = Long.parseLong(currentDirId);


		if (currentUser != null &&
			currentDir != targetDir			
		) {

			// get file from repo
			Files currentFile = filesRepository.findById(fileId).orElse(null);

			// check if file exists
			if (  currentFile != null && 
				  currentFile.getCreatedUserId() == currentUser.getId() &&
				  currentFile.getDirId() == currentDir
				  ) {

				// check if file belongs to user
				if (currentFile.getCreatedUserId() == currentUser.getId()) {

					// update file dir
					currentFile.setDirId(targetDir);

					// save in database
					filesRepository.save(currentFile);

					responseMap.put("error", false);
					responseMap.put("message", "File was moved");
					return ResponseEntity.ok(responseMap);

				} else {
					responseMap.put("error", true);
					responseMap.put("message", "File does not belong to user");
					return ResponseEntity.status(401).body(responseMap);
				}

			} else {
				responseMap.put("error", true);
				responseMap.put("message", "File does not exist");
				return ResponseEntity.status(401).body(responseMap);
			}

		} else {
			responseMap.put("error", true);
			responseMap.put("message", "Invalid name");
			return ResponseEntity.status(401).body(responseMap);
		}
	}

	/*
	 * @PostMapping("/api/uploadMultipleFiles")
	 * public List<File> uploadMultipleFiles(@RequestParam("files") MultipartFile[]
	 * files) {
	 * return Arrays.asList(files)
	 * .stream()
	 * .map(file -> uploadFile(file))
	 * .collect(Collectors.toList());
	 * }
	 */

	@GetMapping("/downloadFile/{fileId:.+}")
	public ResponseEntity<Resource> downloadFile(@PathVariable String fileId,
			HttpServletRequest request) {

		logger.info("Need to load file with id: " + fileId);

		// check auth name
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Users currentUser = usersRepository.getIdByEmail(authentication.getName());
		logger.info(" current user id: " + currentUser.getId());
		logger.info(" current first name: " + currentUser.getFirstName());
		logger.info(" auth name : " + authentication.getName());

		// if userName exists
		if (currentUser != null) {

			// Load file as Resource
			Resource resource = fileStorageService.loadFileAsResource(fileId, Long.toString(currentUser.getId()));

			// Try to determine file's content type
			String contentType = null;
			try {
				contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
			} catch (IOException ex) {
				logger.info("Could not determine file type.");
			}

			// Fallback to the default content type if type could not be determined
			if (contentType == null) {
				contentType = "application/octet-stream";
			}

			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType(contentType))
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" +
							resource.getFilename() + "\"")
					.body(resource);
		}
		// if error
		Resource resource = null;
		return ResponseEntity.status(500)
				.body(resource);
	}
}
