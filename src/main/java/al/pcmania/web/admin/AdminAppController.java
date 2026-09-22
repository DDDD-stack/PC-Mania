package al.pcmania.web.admin;

import al.pcmania.repo.StoredFileRepository;
import al.pcmania.service.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Optional;

/**
 * Hands out the Android admin build from the website.
 *
 * The phone does not have to be plugged into the computer to get a new version: open /admin/app
 * on the phone, sign in as admin and tap download. Behind /admin/**, so only a signed-in operator
 * can reach the file.
 */
@Controller
@RequestMapping("/admin/app")
@RequiredArgsConstructor
public class AdminAppController {

    /** A single slot: uploading a build replaces the one before it. */
    private static final String KEY = "app/pcmania-admin.apk";
    private static final String APK_TYPE = "application/vnd.android.package-archive";
    private static final String DOWNLOAD_NAME = "PCMania-Admin.apk";

    private final FileStorage storage;

    @GetMapping
    String page(Model model) {
        model.addAttribute("file", storage.meta(KEY).orElse(null));
        return "admin/app";
    }

    @PostMapping
    String upload(@RequestParam("apk") MultipartFile apk, RedirectAttributes flash) {
        String name = apk == null ? null : apk.getOriginalFilename();
        if (apk == null || apk.isEmpty()) {
            flash.addFlashAttribute("error", "Nuk u zgjodh asnjë skedar.");
        } else if (name == null || !name.toLowerCase().endsWith(".apk")) {
            flash.addFlashAttribute("error", "Skedari duhet të jetë .apk");
        } else {
            try {
                storage.put(KEY, APK_TYPE, apk.getBytes(), name);
                flash.addFlashAttribute("success", "Aplikacioni u ngarkua.");
            } catch (IOException e) {
                flash.addFlashAttribute("error", "Ngarkimi dështoi: " + e.getMessage());
            }
        }
        return "redirect:/admin/app";
    }

    @GetMapping("/download")
    ResponseEntity<byte[]> download() {
        Optional<StoredFileRepository.Content> found = storage.content(KEY);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(DOWNLOAD_NAME).build().toString())
                .contentType(MediaType.parseMediaType(APK_TYPE))
                // Never cached: the whole point of the page is to hand over the newest build.
                .cacheControl(CacheControl.noStore())
                .body(found.get().getData());
    }

    @PostMapping("/delete")
    String delete(RedirectAttributes flash) {
        storage.delete(KEY);
        flash.addFlashAttribute("success", "Skedari u fshi.");
        return "redirect:/admin/app";
    }
}
