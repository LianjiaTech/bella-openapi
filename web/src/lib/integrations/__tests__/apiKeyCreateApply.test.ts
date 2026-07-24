describe("apiKeyCreateApply", () => {
  const originalUrl = process.env.NEXT_PUBLIC_APIKEY_CREATE_APPLY_URL;

  afterEach(() => {
    jest.resetModules();
    if (originalUrl === undefined) {
      delete process.env.NEXT_PUBLIC_APIKEY_CREATE_APPLY_URL;
    } else {
      process.env.NEXT_PUBLIC_APIKEY_CREATE_APPLY_URL = originalUrl;
    }
  });

  async function loadModule() {
    jest.resetModules();
    return import("../apiKeyCreateApply");
  }

  it("returns null when the BPM create apply entry is not configured", async () => {
    delete process.env.NEXT_PUBLIC_APIKEY_CREATE_APPLY_URL;
    const { buildApiKeyCreateApplyUrl, isApiKeyCreateApplyEnabled } = await loadModule();

    expect(isApiKeyCreateApplyEnabled()).toBe(false);
    expect(buildApiKeyCreateApplyUrl("org")).toBeNull();
  });

  it("returns null for an invalid BPM create apply URL", async () => {
    process.env.NEXT_PUBLIC_APIKEY_CREATE_APPLY_URL = "not-a-url";
    const { buildApiKeyCreateApplyUrl, isApiKeyCreateApplyEnabled } = await loadModule();

    expect(isApiKeyCreateApplyEnabled()).toBe(true);
    expect(buildApiKeyCreateApplyUrl("project")).toBeNull();
  });

  it("builds organization and project BPM create apply URLs", async () => {
    process.env.NEXT_PUBLIC_APIKEY_CREATE_APPLY_URL = "https://bpm.example.com/apply?source=openapi";
    const { buildApiKeyCreateApplyUrl } = await loadModule();

    const orgUrl = new URL(buildApiKeyCreateApplyUrl("org")!);
    expect(orgUrl.origin).toBe("https://bpm.example.com");
    expect(orgUrl.pathname).toBe("/apply");
    expect(orgUrl.searchParams.get("source")).toBe("openapi");
    expect(orgUrl.searchParams.get("prefill")).toBe("1");
    expect(orgUrl.searchParams.get("REQUEST_TYPE")).toBe("CREATE_PARENT_AK");
    expect(orgUrl.searchParams.get("OWNER_TYPE")).toBe("org");

    const projectUrl = new URL(buildApiKeyCreateApplyUrl("project")!);
    expect(projectUrl.searchParams.get("REQUEST_TYPE")).toBe("CREATE_PARENT_AK");
    expect(projectUrl.searchParams.get("OWNER_TYPE")).toBe("project");
  });
});
