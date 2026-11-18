package com.back.was.domain.merchant;

import com.back.was.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "13. 지역 가맹점 (MERCHANT)")
@RestController
@RequestMapping("/regions")
public class RegionController {

    @Operation(summary = "REGION-01 충남 전체 시·군 (지역 선택 화면용)", description = """
            충남 15개 시·군을 모두 돌려줍니다. **`selectable: true` 인 곳(현재 천안·아산)만 선택**할 수 있고,
            나머지는 읽기 전용(비활성)으로 보여 주세요. 선택 가능한 지역이 앞에 옵니다.""")
    @GetMapping
    public ApiResponse<List<ChungnamRegions.Region>> chungnam() {
        return ApiResponse.ok(ChungnamRegions.all());
    }
}
