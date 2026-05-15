package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.dto.response.SectionResponse;
import com.todo.entity.Project;
import com.todo.entity.Section;
import com.todo.exception.AppException;
import com.todo.mapper.SectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionMapper sectionMapper;
    private final ProjectService projectService;

    public List<SectionResponse> list(Long projectId, Long userId) {
        // 权限检查
        projectService.getDetail(projectId, userId); // 复用权限校验
        List<Section> sections = sectionMapper.selectList(
                new LambdaQueryWrapper<Section>()
                        .eq(Section::getProjectId, projectId)
                        .orderByAsc(Section::getSortOrder));
        return sections.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public synchronized SectionResponse create(Long projectId, Long userId, String name) {
        projectService.getDetail(projectId, userId); // 权限检查
        // 确定 sort_order
        Integer max = sectionMapper.selectList(new LambdaQueryWrapper<Section>()
                        .eq(Section::getProjectId, projectId)
                        .orderByDesc(Section::getSortOrder)
                        .last("LIMIT 1"))
                .stream().findFirst().map(Section::getSortOrder).orElse(-1);

        Section section = new Section();
        section.setProjectId(projectId);
        section.setName(name);
        section.setSortOrder(max + 1);
        sectionMapper.insert(section);
        return toResponse(section);
    }

    public SectionResponse update(Long sectionId, Long userId, String name) {
        Section section = sectionMapper.selectById(sectionId);
        if (section == null) throw AppException.notFound("分区");
        // 再次校验项目权限
        projectService.getDetail(section.getProjectId(), userId);
        section.setName(name);
        sectionMapper.updateById(section);
        return toResponse(section);
    }

    @Transactional
    public void delete(Long sectionId, Long userId) {
        Section section = sectionMapper.selectById(sectionId);
        if (section == null) throw AppException.notFound("分区");
        projectService.getDetail(section.getProjectId(), userId);
        sectionMapper.deleteById(sectionId);
    }

    @Transactional
    public synchronized void reorder(Long projectId, Long userId, List<Long> orderedIds) {
        projectService.getDetail(projectId, userId);
        List<Section> sections = sectionMapper.selectBatchIds(orderedIds);
        java.util.Map<Long, Section> sectionMap = sections.stream()
                .filter(s -> s != null && s.getProjectId().equals(projectId))
                .collect(java.util.stream.Collectors.toMap(
                        com.todo.entity.Section::getId, s -> s));
        for (int i = 0; i < orderedIds.size(); i++) {
            Section section = sectionMap.get(orderedIds.get(i));
            if (section != null) {
                section.setSortOrder(i);
                sectionMapper.updateById(section);
            }
        }
    }

    private SectionResponse toResponse(Section section) {
        SectionResponse resp = new SectionResponse();
        resp.setId(section.getId());
        resp.setProjectId(section.getProjectId());
        resp.setName(section.getName());
        resp.setSortOrder(section.getSortOrder());
        resp.setCreateTime(section.getCreateTime());
        return resp;
    }
}