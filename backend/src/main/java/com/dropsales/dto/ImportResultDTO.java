package com.dropsales.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ImportResultDTO {
    private int importados;
    private int atualizados;
    private int ignorados;
    private List<String> erros;
}