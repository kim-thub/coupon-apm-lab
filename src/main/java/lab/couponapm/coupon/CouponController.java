package lab.couponapm.coupon;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lab.couponapm.apm.ApmTrace;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {
    private final CouponService service;

    public CouponController(CouponService service) { this.service = service; }

    public record CreateRequest(@NotBlank @Size(max = 100) String name, @NotNull @Min(0) Integer totalStock) { }
    public record IssueRequest(@NotBlank @Size(max = 100) String userId) { }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse create(@Valid @RequestBody CreateRequest request) {
        return service.create(request.name(), request.totalStock());
    }

    @GetMapping("/{couponId}")
    public CouponResponse get(@PathVariable Long couponId) { return service.get(couponId); }

    @GetMapping
    public List<CouponResponse> list() { return service.list(); }

    @ApmTrace
    @PostMapping("/{couponId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponIssueResponse issue(@PathVariable Long couponId, @Valid @RequestBody IssueRequest request) {
        return service.issueCoupon(couponId, request.userId());
    }
}
