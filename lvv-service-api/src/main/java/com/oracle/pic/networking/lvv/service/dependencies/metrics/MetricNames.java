package com.oracle.pic.networking.lvv.service.dependencies.metrics;

public class MetricNames {
    public enum MetricScopeNames {
        ADD_PROJECT_ITEM,
        UPDATE_PROJECT_ITEM,
        DELETE_PROJECT_ITEM,
        GET_PROJECT_ITEM,
        CABLING_TASKS,
        VALIDATE_CABLES,
        GET_VALIDATION_RESULTS,
        DOWNLOAD_VALIDATION_RESULTS,
        GET_NCP_VALIDATION_JOB_STATUS,
        ADD_VALIDATION_RESULTS,
        BLOCK_DETAILS,
        PROCESS_NCP_JOB_OUTPUT,
        UPDATE_LINK_RESULTS,
        FETCH_REGIONS,
        RACK_DETAILS,
        FETCH_RACKS,
        NCP_JOB_DETAILS
    }

    public enum AddProjectItem {
        AddProject,
        ItemAlreadyExists,
        BlockDetailsEmpty,
        BlockAlreadyAssigned,
        KievCommitFailure,
    }

    public enum UpdateProjectItem {
        UpdateProject,
        ItemDoesNotExist,
        BlockDetailsEmpty,
        BlockAlreadyAssigned,
        KievCommitFailure
    }

    public enum DeleteProjectItem {
        DeleteProject,
        ItemDoesNotExist,
        KievCommitFailure,
        ProjectIdEmpty
    }

    public enum GetProjectItem {
        GetProject,
        GetProjectsForVendor,
        GetAllProjects,
        ProjectIdEmpty,
        ProjectNotFound,
        NoBlocksInProject,
        VendorNameEmpty
    }

    public enum CablingTasks {
        GetCablingTasksForProject,
        GetCablingTasksForBlock,
        GetClosedCablingTasks,
        ResolveCablingTask,
        ResolveDisabledRegion,
        RegionMissing
    }

    public enum GetValidationResults {
        GetValidationResult,
        RackSerialNull
    }

    public enum ValidateCables {
        ValidateCable,
        MissingParameters,
        NcpJobCreationFailed,
        NcpJobTimeout,
        NcpJobFail,
        NcpJobPollingFail,
        NcpJobCompleted,
        KievResultAddFailure,
        DownloadCsv
    }

    public enum GetValidationJobStatus {
        JobIdNull,
        BuildingNull,
        RackSerialNull,
        RackNumberNull,
        GetJobStatus,
        Fail,
        Pending,
        Success,
        Unknown
    }

    public enum AddValidationResults {
        KievResultUpdateFailure,
        NoMoreFailures
    }

    public enum BlockDetails {
        AddBlockDetails,
        DeleteBlockDetails,
        GetBlockDetails,
        GetBlockDetailsForProject,
        BlockSizeExceedsMaximum,
        NoBlocksInProject
    }

    public enum NcpJobDetails {
        AddNcpJobDetails,
    }

    public enum ValidationDuration {
        DeviceValidationDuration,
        RackValidationDuration
    }

    public enum ProcessNcpResult {
        JsonParseFail,
        NoTestResultFound,
        LldpError,
        OpticError,
        PsuError,
        InterfaceError,
        FecBerError,
        FanError,
        Pass,
        LldpErrorFormatUnexpected,
        OpticErrorFormatUnexpected,
        InterfaceErrorFormatUnexpected,
        FecBerErrorFormatUnexpected,
        FanErrorFormatUnexpected
    }

    public enum UpdateLinkResults {
        TimeFromLastValidation
    }

    public enum FetchRegions {
        RealmEmpty,
        NoRegionsFound
    }

    public enum RackDetails {
        MissingParameters,
        FetchDevices,
        FetchDevicesFailed,
        DevicesNotFound
    }

    public enum FetchRacks {
        ProjectIdNull,
        RegionNameNull,
        ProjectNotFound,
        NoBlocksInProject,
        FetchRacks,
        FetchRacksFailed,
        ResolveDisabledRegionCount,
        RegionMissing
    }
}
