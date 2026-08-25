#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <wchar.h>

#define PATH_BUFFER_SIZE 32768

static void show_error(const wchar_t *message)
{
    DWORD error = GetLastError();
    wchar_t detail[512] = L"";
    wchar_t text[1024];

    FormatMessageW(
        FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
        NULL,
        error,
        0,
        detail,
        (DWORD)(sizeof(detail) / sizeof(detail[0])),
        NULL);
    _snwprintf_s(text, sizeof(text) / sizeof(text[0]), _TRUNCATE,
                 L"%s\n\nWindows error %lu: %s", message, error, detail);
    MessageBoxW(NULL, text, L"TCHESS", MB_OK | MB_ICONERROR);
}

static BOOL file_exists(const wchar_t *path)
{
    DWORD attributes = GetFileAttributesW(path);
    return attributes != INVALID_FILE_ATTRIBUTES &&
           (attributes & FILE_ATTRIBUTE_DIRECTORY) == 0;
}

int APIENTRY wWinMain(
    _In_ HINSTANCE instance,
    _In_opt_ HINSTANCE previous_instance,
    _In_ LPWSTR command_line,
    _In_ int show_command)
{
    wchar_t launcher_path[PATH_BUFFER_SIZE];
    wchar_t java_path[PATH_BUFFER_SIZE];
    wchar_t jar_path[PATH_BUFFER_SIZE];
    wchar_t java_command[PATH_BUFFER_SIZE * 2];
    wchar_t *last_separator;
    STARTUPINFOW startup_info = {0};
    PROCESS_INFORMATION process_info = {0};
    DWORD path_length;

    UNREFERENCED_PARAMETER(instance);
    UNREFERENCED_PARAMETER(previous_instance);
    UNREFERENCED_PARAMETER(command_line);
    UNREFERENCED_PARAMETER(show_command);

    path_length = GetModuleFileNameW(NULL, launcher_path, PATH_BUFFER_SIZE);
    if (path_length == 0 || path_length >= PATH_BUFFER_SIZE) {
        show_error(L"Cannot locate the TCHESS application directory.");
        return 1;
    }

    last_separator = wcsrchr(launcher_path, L'\\');
    if (last_separator == NULL) {
        SetLastError(ERROR_BAD_PATHNAME);
        show_error(L"Cannot locate the TCHESS application directory.");
        return 1;
    }
    *last_separator = L'\0';

    if (_snwprintf_s(java_path, PATH_BUFFER_SIZE, _TRUNCATE,
                     L"%s\\java\\bin\\javaw.exe", launcher_path) < 0 ||
        _snwprintf_s(jar_path, PATH_BUFFER_SIZE, _TRUNCATE,
                     L"%s\\app.jar", launcher_path) < 0) {
        SetLastError(ERROR_BUFFER_OVERFLOW);
        show_error(L"The TCHESS application path is too long.");
        return 1;
    }

    if (!file_exists(java_path)) {
        SetLastError(ERROR_FILE_NOT_FOUND);
        show_error(L"Cannot find java\\bin\\javaw.exe. Extract the complete package first.");
        return 1;
    }
    if (!file_exists(jar_path)) {
        SetLastError(ERROR_FILE_NOT_FOUND);
        show_error(L"Cannot find app.jar. Extract the complete package first.");
        return 1;
    }

    if (_snwprintf_s(java_command, PATH_BUFFER_SIZE * 2, _TRUNCATE,
                     L"\"%s\" -jar \"%s\"", java_path, jar_path) < 0) {
        SetLastError(ERROR_BUFFER_OVERFLOW);
        show_error(L"The TCHESS application path is too long.");
        return 1;
    }

    startup_info.cb = sizeof(startup_info);
    if (!CreateProcessW(
            java_path,
            java_command,
            NULL,
            NULL,
            FALSE,
            0,
            NULL,
            launcher_path,
            &startup_info,
            &process_info)) {
        show_error(L"Failed to start the TCHESS Java application.");
        return 1;
    }

    CloseHandle(process_info.hThread);
    CloseHandle(process_info.hProcess);
    return 0;
}
