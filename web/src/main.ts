import JSZip from "jszip";
import * as XLSX from "xlsx";
import "./styles.css";

type CellValue = string | number | boolean | null | undefined;
type SheetRows = CellValue[][];

type Config = {
  fileName: string;
  periods: number;
  agents: number;
  contacts: number;
  friends: number;
  levels: number;
  repetitions: number;
  gui: boolean;
  base: number;
  memory: number;
  sourceReach: boolean;
  wom: boolean;
  scenario: number;
  learningPeriods: number;
  compressedResults: boolean;
  savedEndorsements: boolean;
  savedAgentDecisions: boolean;
  savedDetailedAgentDecisions: boolean;
  savedRepostsPerSource: boolean;
  savedFakeNews: boolean;
  newsSources: number;
  attributesSource: number;
  attributesUser: number;
};

type ScenarioInput = {
  from: string;
  to: string;
  start: number;
  attributes: string[];
};

type InputModel = {
  name: string;
  config: Config;
  warnings: string[];
  sheets: Record<string, SheetRows>;
  newsSources: InnerNewsSource[];
  snsUserPrototype: InnerSNSUser;
  scenario?: ScenarioInput;
};

type InnerNewsSource = {
  name: string;
  reach: number;
  attributeNames: string[];
  attributeValues: number[][];
};

type InnerSNSUser = {
  attributeNames: string[];
  attributeValues: number[];
};

type Endorsement = {
  period: number;
  newsSource: NewsSource;
  attributeName: string;
  value: number;
};

type AgentDecisionData = {
  simulationId: number;
  period: number;
  snsUserId: number;
  newsSourceName: string;
  evaluation: number;
};

type EndorsementData = {
  simulationId: number;
  period: number;
  snsUserId: number;
  newsSourceName: string;
  attribute: string;
  value: number;
};

type RepostsData = {
  simulationId: number;
  period: number;
  reposts: number[];
};

type FakeNewsData = {
  simulationId: number;
  period: number;
  fakeNews: number[];
};

type SimulationOutput = {
  workbookBlob: Blob;
  zipBlob: Blob;
  imageUrls: string[];
  zipUrl: string;
  workbookUrl: string;
  elapsedMs: number;
};

type LogLevel = "INFO" | "WARN" | "ERROR" | "DEBUG";
type CsvExport = {
  sheetName: string;
  fileName: string;
  rows: CellValue[][];
};
type ConfigEditableKey =
  | "periods"
  | "agents"
  | "contacts"
  | "friends"
  | "levels"
  | "repetitions"
  | "gui"
  | "base"
  | "memory"
  | "sourceReach"
  | "wom"
  | "scenario"
  | "learningPeriods"
  | "savedEndorsements"
  | "savedRepostsPerSource"
  | "savedFakeNews"
  | "savedDetailedAgentDecisions"
  | "savedAgentDecisions"
  | "compressedResults";

type ConfigField = {
  label: string;
  key: ConfigEditableKey;
  min?: number;
  max?: number;
  step?: number;
  integer?: boolean;
  boolean?: boolean;
};

const EXAMPLES = [
  {
    name: "Baseline",
    fileName: "FAKENEWS_BASELINE.xlsx",
    path: `${import.meta.env.BASE_URL}examples/FAKENEWS_BASELINE.xlsx`,
    description: "Default media ecosystem with word of mouth enabled."
  },
  {
    name: "Coordinated Push",
    fileName: "FAKENEWS_COORDINATED_PUSH.xlsx",
    path: `${import.meta.env.BASE_URL}examples/FAKENEWS_COORDINATED_PUSH.xlsx`,
    description: "After period 15, unknown media adopts selected fake-news attributes."
  }
];

const REQUIRED_CONFIG = [
  "PERIODS",
  "AGENTS",
  "CONTACTS",
  "FRIENDS",
  "LEVELS",
  "REPETITIONS",
  "GUI",
  "BASE",
  "MEMORY",
  "SOURCE_REACH",
  "WOM",
  "SCENARIO",
  "LEARNING_PERIODS",
  "SAVED_ENDORSEMENTS",
  "SAVED_REPOSTS_PER_SOURCE",
  "SAVED_DETAILED_AGENT_DECISIONS",
  "SAVED_AGENT_DECISIONS",
  "COMPRESSED_RESULTS"
];

const CONFIG_FIELDS: ConfigField[] = [
  { label: "PERIODS", key: "periods", min: 1, step: 1, integer: true },
  { label: "AGENTS", key: "agents", min: 1, step: 1, integer: true },
  { label: "CONTACTS", key: "contacts", min: 0, step: 1, integer: true },
  { label: "FRIENDS", key: "friends", min: 0, max: 1, step: 0.01 },
  { label: "LEVELS", key: "levels", min: 2, step: 1, integer: true },
  { label: "REPETITIONS", key: "repetitions", min: 0, step: 1, integer: true },
  { label: "GUI", key: "gui", min: 0, max: 1, step: 1, integer: true, boolean: true },
  { label: "BASE", key: "base", min: 0, step: 0.01 },
  { label: "MEMORY", key: "memory", step: 1, integer: true },
  { label: "SOURCE_REACH", key: "sourceReach", min: 0, max: 1, step: 1, integer: true, boolean: true },
  { label: "WOM", key: "wom", min: 0, max: 1, step: 1, integer: true, boolean: true },
  { label: "SCENARIO", key: "scenario", step: 1, integer: true },
  { label: "LEARNING_PERIODS", key: "learningPeriods", min: 0, step: 1, integer: true },
  { label: "SAVED_ENDORSEMENTS", key: "savedEndorsements", min: 0, max: 1, step: 1, integer: true, boolean: true },
  { label: "SAVED_REPOSTS_PER_SOURCE", key: "savedRepostsPerSource", min: 0, max: 1, step: 1, integer: true, boolean: true },
  { label: "SAVED_FAKENEWS", key: "savedFakeNews", min: 0, max: 1, step: 1, integer: true, boolean: true },
  { label: "SAVED_DETAILED_AGENT_DECISIONS", key: "savedDetailedAgentDecisions", min: 0, max: 1, step: 1, integer: true, boolean: true },
  { label: "SAVED_AGENT_DECISIONS", key: "savedAgentDecisions", min: 0, max: 1, step: 1, integer: true, boolean: true },
  { label: "COMPRESSED_RESULTS", key: "compressedResults", min: 0, max: 1, step: 1, integer: true, boolean: true }
];

const DISABLED_SCENARIO = -1;
const CUSTOM_SCENARIO = -2;
const MAX_XLSX_ROWS = 100_000;
const app = document.querySelector<HTMLDivElement>("#app");

if (!app) {
  throw new Error("App container not found");
}

app.innerHTML = `
  <main class="shell">
    <aside class="panel">
      <div class="brand">
        <span class="mark">FN</span>
        <div>
          <h1>FAKENEWS-ABM</h1>
          <p>Browser simulation workbench</p>
        </div>
      </div>

      <section class="section">
        <h2>Input</h2>
        <label class="file-picker">
          <input id="fileInput" type="file" accept=".xlsx,.xls" />
          <span>Load Excel workbook</span>
        </label>
        <div class="examples" id="examples"></div>
      </section>

      <section class="section">
        <h2>Configuration</h2>
        <div class="config-table" id="configFields"></div>
      </section>

      <section class="section contact-links" aria-label="Contact links">
        <h2>Links</h2>
        <a class="icon-link" href="https://github.com/pleger/FAKENEWS-ABM" target="_blank" rel="noreferrer" title="GitHub repository">
          <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M12 2a10 10 0 0 0-3.16 19.49c.5.09.68-.22.68-.48v-1.7c-2.78.6-3.37-1.18-3.37-1.18-.45-1.15-1.1-1.46-1.1-1.46-.9-.61.07-.6.07-.6 1 .07 1.53 1.03 1.53 1.03.88 1.52 2.32 1.08 2.89.83.09-.65.35-1.08.63-1.33-2.22-.25-4.56-1.11-4.56-4.94 0-1.09.39-1.98 1.03-2.68-.1-.25-.45-1.27.1-2.64 0 0 .84-.27 2.75 1.02A9.56 9.56 0 0 1 12 6.02c.85 0 1.7.11 2.5.34 1.9-1.29 2.74-1.02 2.74-1.02.55 1.37.2 2.39.1 2.64.64.7 1.03 1.59 1.03 2.68 0 3.84-2.34 4.68-4.57 4.93.36.31.68.92.68 1.86v2.76c0 .27.18.58.69.48A10 10 0 0 0 12 2Z"/></svg>
          <span>GitHub</span>
        </a>
        <a class="icon-link" href="https://pleger.cl" target="_blank" rel="noreferrer" title="Personal website">
          <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm6.93 9h-3.19a15.8 15.8 0 0 0-1.05-5.03A8.03 8.03 0 0 1 18.93 11ZM12 4.04c.7 1.01 1.52 3.05 1.72 6.96h-3.44c.2-3.91 1.02-5.95 1.72-6.96ZM4.26 13h3.99c.12 2.1.5 3.9 1.06 5.03A8.03 8.03 0 0 1 4.26 13Zm3.99-2H4.26a8.03 8.03 0 0 1 5.05-5.03A15.8 15.8 0 0 0 8.25 11ZM12 19.96c-.7-1.01-1.52-3.05-1.72-6.96h3.44c-.2 3.91-1.02 5.95-1.72 6.96Zm2.69-1.93c.56-1.13.94-2.93 1.06-5.03h3.99a8.03 8.03 0 0 1-5.05 5.03Z"/></svg>
          <span>Website</span>
        </a>
        <a class="icon-link" href="https://www.linkedin.com/in/plegerm/" target="_blank" rel="noreferrer" title="LinkedIn profile">
          <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M6.94 8.98H3.7V20h3.24V8.98ZM5.32 4a1.88 1.88 0 1 0 0 3.76A1.88 1.88 0 0 0 5.32 4Zm15 9.68c0-3.36-1.79-4.93-4.18-4.93a3.61 3.61 0 0 0-3.25 1.79V8.98H9.78V20h3.24v-5.45c0-1.44.27-2.84 2.06-2.84 1.76 0 1.78 1.65 1.78 2.93V20h3.24v-6.32h.22Z"/></svg>
          <span>LinkedIn</span>
        </a>
        <a class="icon-link" href="mailto:pleger@ucn.cl" title="Email Paul Leger">
          <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Zm8 7.15L4.62 7H4v.7l8 5.6 8-5.6V7h-.62L12 12.15Z"/></svg>
          <span>pleger@ucn.cl</span>
        </a>
      </section>
    </aside>

    <section class="workspace">
      <div class="runbar">
        <div>
          <p id="inputName" class="eyebrow">No workbook loaded</p>
          <h2 id="headline">Load an Excel input or choose an example.</h2>
        </div>
        <button id="runButton" class="primary" disabled>Run</button>
      </div>

      <div class="status-grid">
        <article>
          <span id="sourceCount">0</span>
          <p>News sources</p>
        </article>
        <article>
          <span id="attributeCount">0</span>
          <p>Attributes</p>
        </article>
        <article>
          <span id="periodCount">0</span>
          <p>Total periods</p>
        </article>
      </div>

      <div class="progress-wrap">
        <div class="progress-meta">
          <span id="progressLabel">Idle</span>
          <span id="progressValue">0%</span>
        </div>
        <progress id="progress" max="100" value="0"></progress>
      </div>

      <div id="validation" class="validation"></div>

      <section class="results">
        <div class="result-actions">
          <button id="showImages" disabled>Show generated images</button>
          <a id="downloadZip" class="download disabled" href="#" download>Download ZIP</a>
          <a id="downloadWorkbook" class="download disabled" href="#" download>Download workbook</a>
        </div>
        <div id="preview" class="preview"></div>
      </section>

      <section class="log-section">
        <div class="log-head">
          <h2>Simulation log</h2>
          <div class="log-actions">
            <a id="downloadLog" class="download disabled" href="#" download="simulation-log.txt">Download log</a>
            <button id="clearLog">Clear</button>
          </div>
        </div>
        <pre id="log"></pre>
      </section>
    </section>
  </main>

  <dialog id="imageDialog">
    <div class="dialog-head">
      <h2>Generated images</h2>
      <button id="closeDialog">Close</button>
    </div>
    <div id="dialogImages" class="dialog-images"></div>
  </dialog>
`;

const els = {
  examples: byId<HTMLDivElement>("examples"),
  fileInput: byId<HTMLInputElement>("fileInput"),
  runButton: byId<HTMLButtonElement>("runButton"),
  inputName: byId<HTMLParagraphElement>("inputName"),
  headline: byId<HTMLHeadingElement>("headline"),
  configFields: byId<HTMLDivElement>("configFields"),
  sourceCount: byId<HTMLSpanElement>("sourceCount"),
  attributeCount: byId<HTMLSpanElement>("attributeCount"),
  periodCount: byId<HTMLSpanElement>("periodCount"),
  progress: byId<HTMLProgressElement>("progress"),
  progressLabel: byId<HTMLSpanElement>("progressLabel"),
  progressValue: byId<HTMLSpanElement>("progressValue"),
  validation: byId<HTMLDivElement>("validation"),
  log: byId<HTMLPreElement>("log"),
  clearLog: byId<HTMLButtonElement>("clearLog"),
  downloadLog: byId<HTMLAnchorElement>("downloadLog"),
  showImages: byId<HTMLButtonElement>("showImages"),
  downloadZip: byId<HTMLAnchorElement>("downloadZip"),
  downloadWorkbook: byId<HTMLAnchorElement>("downloadWorkbook"),
  preview: byId<HTMLDivElement>("preview"),
  imageDialog: byId<HTMLDialogElement>("imageDialog"),
  closeDialog: byId<HTMLButtonElement>("closeDialog"),
  dialogImages: byId<HTMLDivElement>("dialogImages")
};

let loadedInput: InputModel | null = null;
let lastOutput: SimulationOutput | null = null;
let logDownloadUrl: string | null = null;
const logLines: string[] = [];

renderExamples();
renderConfigFields(null);
wireEvents();
log("INFO", "Web application ready. Load an Excel workbook or choose an example.");

function byId<T extends HTMLElement>(id: string): T {
  const el = document.getElementById(id);
  if (!el) throw new Error(`Missing element #${id}`);
  return el as T;
}

function renderExamples(): void {
  els.examples.innerHTML = "";
  for (const example of EXAMPLES) {
    const button = document.createElement("button");
    button.type = "button";
    button.innerHTML = `<strong>${example.name}</strong><span>${example.description}</span>`;
    button.addEventListener("click", () => loadExample(example.path, example.fileName));
    els.examples.append(button);
  }
}

function renderConfigFields(config: Config | null): void {
  els.configFields.innerHTML = CONFIG_FIELDS.map((field) => {
    const value = config ? configValueToInput(config, field) : "";
    const min = field.min == null ? "" : ` min="${field.min}"`;
    const max = field.max == null ? "" : ` max="${field.max}"`;
    const step = ` step="${field.step ?? 1}"`;
    const disabled = config ? "" : " disabled";
    const mode = field.boolean ? " data-binary=\"true\"" : "";
    return `
      <label class="config-row">
        <span>${field.label}</span>
        <input type="number" inputmode="decimal" data-config-key="${field.key}" value="${value}"${min}${max}${step}${mode}${disabled} />
      </label>
    `;
  }).join("");
}

function wireEvents(): void {
  els.fileInput.addEventListener("change", async () => {
    const file = els.fileInput.files?.[0];
    if (!file) return;
    await loadWorkbook(await file.arrayBuffer(), file.name);
  });

  els.runButton.addEventListener("click", () => runCurrentSimulation());
  els.clearLog.addEventListener("click", () => {
    logLines.length = 0;
    els.log.textContent = "";
    refreshLogDownload();
  });
  els.showImages.addEventListener("click", () => showImageDialog());
  els.closeDialog.addEventListener("click", () => els.imageDialog.close());
  els.configFields.addEventListener("input", syncConfigFromControls);
  els.configFields.addEventListener("change", syncConfigFromControls);
}

async function loadExample(path: string, fileName: string): Promise<void> {
  try {
    log("INFO", `Loader: Reading input from bundled example: ${fileName}`);
    const response = await fetch(path);
    if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
    await loadWorkbook(await response.arrayBuffer(), fileName);
  } catch (error) {
    log("ERROR", `Example cannot be loaded: ${String(error)}`);
  }
}

async function loadWorkbook(buffer: ArrayBuffer, fileName: string): Promise<void> {
  try {
    setBusy(true, "Loading workbook");
    const workbook = XLSX.read(buffer, { type: "array" });
    log("INFO", `Loader: Sheets available in the input file: ${workbook.SheetNames.join(",")}`);
    const model = parseWorkbook(workbook, fileName);
    loadedInput = model;
    lastOutput = null;
    updateLoadedInputUi(model);
    resetResults();
    log("INFO", `MAIN: Configuration loaded -> {${formatConfig(model.config)}}`);
    for (const warning of model.warnings) log("WARN", warning);
  } catch (error) {
    loadedInput = null;
    updateLoadedInputUi(null);
    log("ERROR", `Loader.read: Input cannot be open: ${String(error)}`);
  } finally {
    setBusy(false, "Idle");
  }
}

function parseWorkbook(workbook: XLSX.WorkBook, fileName: string): InputModel {
  const warnings: string[] = [];
  const sheets: Record<string, SheetRows> = {};
  for (const name of workbook.SheetNames) {
    const sheet = workbook.Sheets[name];
    sheets[name] = XLSX.utils.sheet_to_json<CellValue[]>(sheet, { header: 1, raw: true, defval: null });
  }

  for (const required of ["Configuration", "NewsSources", "SNSUsers", "SourceReach"]) {
    if (!sheets[required]) throw new Error(`Sheet '${required}' has not been loaded`);
  }

  const configMap = readKeyValueSheet(sheets.Configuration);
  for (const param of REQUIRED_CONFIG) {
    if (!configMap.has(param)) warnings.push(`${param} is missing.`);
  }

  const config = buildConfig(configMap, stripExtension(fileName));
  if (configMap.has("SAVED_FAKENEWS") && ![0, 1].includes(configMap.get("SAVED_FAKENEWS")!)) {
    throw new Error("SAVED_FAKENEWS must be 0 or 1.");
  }
  if (config.levels < 2) throw new Error("LEVELS must be greater than 1.");

  const sourceReach = readSourceReach(sheets.SourceReach);
  const newsSources = readNewsSources(sheets.NewsSources, config.levels, sourceReach);
  const snsUserPrototype = readSnsUsers(sheets.SNSUsers);
  config.attributesSource = newsSources[0]?.attributeNames.length ?? 0;
  config.attributesUser = snsUserPrototype.attributeNames.length;
  config.newsSources = newsSources.length;

  validateModel(config, newsSources, snsUserPrototype, warnings);

  let scenario: ScenarioInput | undefined;
  if (config.scenario !== DISABLED_SCENARIO) {
    if (!sheets.Scenario) throw new Error("Sheet 'Scenario' has not been loaded");
    scenario = readScenario(sheets.Scenario);
    if (config.scenario !== CUSTOM_SCENARIO) {
      warnings.push(`SCENARIO=${config.scenario} is not implemented in the browser port; use -2 for a custom Scenario sheet.`);
    }
  }

  return { name: fileName, config, warnings, sheets, newsSources, snsUserPrototype, scenario };
}

function readKeyValueSheet(rows: SheetRows): Map<string, number> {
  const result = new Map<string, number>();
  for (const row of rows) {
    const key = text(row[0]);
    if (!key) continue;
    result.set(key.toUpperCase(), num(row[1]));
  }
  return result;
}

function buildConfig(conf: Map<string, number>, fileName: string): Config {
  return {
    fileName,
    periods: intConf(conf, "PERIODS", 30),
    agents: intConf(conf, "AGENTS", 10),
    contacts: intConf(conf, "CONTACTS", 17),
    friends: numConf(conf, "FRIENDS", 0.7),
    levels: intConf(conf, "LEVELS", 2),
    repetitions: intConf(conf, "REPETITIONS", 0),
    gui: boolConf(conf, "GUI", false),
    base: numConf(conf, "BASE", 1.2),
    memory: intConf(conf, "MEMORY", -1),
    sourceReach: boolConf(conf, "SOURCE_REACH", false),
    wom: boolConf(conf, "WOM", false),
    scenario: intConf(conf, "SCENARIO", -1),
    learningPeriods: intConf(conf, "LEARNING_PERIODS", 100),
    compressedResults: boolConf(conf, "COMPRESSED_RESULTS", false),
    savedEndorsements: boolConf(conf, "SAVED_ENDORSEMENTS", false),
    savedAgentDecisions: boolConf(conf, "SAVED_AGENT_DECISIONS", false),
    savedDetailedAgentDecisions: boolConf(conf, "SAVED_DETAILED_AGENT_DECISIONS", false),
    savedRepostsPerSource: boolConf(conf, "SAVED_REPOSTS_PER_SOURCE", false),
    savedFakeNews: boolConf(conf, "SAVED_FAKENEWS", false),
    newsSources: 0,
    attributesSource: 0,
    attributesUser: 0
  };
}

function readSourceReach(rows: SheetRows): Map<string, number> {
  log("INFO", "Loader: Reading NewsSource Reach");
  const reach = new Map<string, number>();
  for (const row of rows) {
    const name = text(row[0]);
    if (name) reach.set(name.toUpperCase(), num(row[1]) / 100);
  }
  return reach;
}

function readNewsSources(rows: SheetRows, levels: number, reach: Map<string, number>): InnerNewsSource[] {
  log("INFO", "Loader: Reading NewsSource Attributes");
  const names: string[] = [];
  const header = rows[1] ?? [];
  for (let column = 1; column < header.length; column += 1) {
    if ((column + 1) % levels === 0) names.push(text(header[column]).toUpperCase());
  }

  const sources = names.map((name) => ({
    name,
    reach: reach.get(name) ?? 1,
    attributeNames: [] as string[],
    attributeValues: [] as number[][]
  }));

  for (let rowIndex = 3; rowIndex < rows.length; rowIndex += 1) {
    const row = rows[rowIndex];
    const attrName = text(row[0]).toUpperCase();
    if (!attrName) continue;
    const values: number[][] = [];
    let current: number[] = [];
    for (let column = 1; column < row.length; column += 1) {
      current.push(num(row[column]));
      if (column % levels === 0) {
        values.push(current);
        current = [];
      }
    }
    sources.forEach((source, index) => {
      source.attributeNames.push(attrName);
      source.attributeValues.push(values[index] ?? Array(levels).fill(0));
    });
  }

  if (sources.length === 0) throw new Error("No news sources were found in NewsSources.");
  return sources;
}

function readSnsUsers(rows: SheetRows): InnerSNSUser {
  log("INFO", "Loader: Reading SNSUsers");
  const user: InnerSNSUser = { attributeNames: [], attributeValues: [] };
  for (const row of rows) {
    const attrName = text(row[0]).toUpperCase();
    if (!attrName) continue;
    user.attributeNames.push(attrName);
    user.attributeValues.push(num(row[1]));
  }
  return user;
}

function readScenario(rows: SheetRows): ScenarioInput {
  log("INFO", "Loader: Reading Scenario");
  const row = rows[0] ?? [];
  return {
    from: text(row[0]).toUpperCase(),
    to: text(row[1]).toUpperCase(),
    start: Math.trunc(num(row[2])),
    attributes: row.slice(3).map((value) => text(value).toUpperCase()).filter(Boolean)
  };
}

function validateModel(config: Config, newsSources: InnerNewsSource[], snsUser: InnerSNSUser, warnings: string[]): void {
  if (config.agents < 1) throw new Error("AGENTS must be at least 1.");
  if (config.periods < 1) throw new Error("PERIODS must be at least 1.");
  if (config.repetitions < 0) throw new Error("REPETITIONS must be zero or greater.");
  if (config.friends < 0 || config.friends > 1) warnings.push("FRIENDS is usually expected to be between 0 and 1.");
  if (![2, 3].includes(config.levels)) warnings.push("LEVELS is normally 2 or 3 in the Java model.");

  const sourceAttrs = new Set(newsSources[0]?.attributeNames ?? []);
  for (const source of newsSources) {
    if (source.attributeNames.length !== sourceAttrs.size) {
      throw new Error(`News source ${source.name} has a different attribute count.`);
    }
    for (const values of source.attributeValues) {
      if (values.length !== config.levels) throw new Error(`News source ${source.name} has a row that does not match LEVELS=${config.levels}.`);
      const sum = values.reduce((acc, value) => acc + value, 0);
      if (Math.abs(sum - 1) > 0.05) warnings.push(`${source.name} has an attribute distribution with sum ${sum.toFixed(2)}.`);
    }
  }

  for (const attr of sourceAttrs) {
    if (!snsUser.attributeNames.includes(attr)) warnings.push(`SNSUsers is missing weight for source attribute ${attr}.`);
  }
  if (config.wom && !snsUser.attributeNames.includes("WORD OF MOUTH")) {
    warnings.push("WOM is enabled but SNSUsers does not include WORD OF MOUTH.");
  }
}

async function runCurrentSimulation(): Promise<void> {
  if (!loadedInput) return;
  syncConfigFromControls();
  resetResults();
  setBusy(true, "Running simulation");
  const start = performance.now();
  try {
    const runner = new SimulationRunner(loadedInput, updateProgress);
    const runResult = await runner.run();
    const output = await createOutputs(loadedInput, runResult, performance.now() - start);
    lastOutput = output;
    displayOutput(output);
    log("INFO", `Main: Simulation executions took ${(output.elapsedMs / 1000).toFixed(2)} seconds`);
    log("INFO", "Main: End.");
  } catch (error) {
    log("ERROR", `Simulation failed: ${formatError(error)}`);
  } finally {
    setBusy(false, "Complete");
  }
}

class SimulationRunner {
  private readonly model: InputModel;
  private readonly reporter = new ReporterStore();
  private readonly onProgress: (done: number, total: number, label: string) => void;
  private simulationId = 0;

  constructor(model: InputModel, onProgress: (done: number, total: number, label: string) => void) {
    this.model = model;
    this.onProgress = onProgress;
  }

  async run(): Promise<ReporterStore> {
    const totalRuns = this.model.config.repetitions + 1;
    const totalSteps = totalRuns * this.model.config.periods;
    let done = 0;

    const users = Array.from({ length: this.model.config.agents }, (_, id) => new SNSUser(id, this.model.snsUserPrototype));
    const sources = this.model.newsSources.map((source, id) => new NewsSource(id, source));
    const simulation = new Simulation(users, sources, this.model, this.reporter, () => ++this.simulationId);

    for (let run = 1; run <= totalRuns; run += 1) {
      log("INFO", simulation.toString());
      log("INFO", `Simulation: Starting ${simulation.id}`);
      for (let period = 1; period <= this.model.config.periods; period += 1) {
        simulation.step(period);
        done += 1;
        this.onProgress(done, totalSteps, `Run ${run} of ${totalRuns}, period ${period}`);
        if (period % 2 === 0) await nextFrame();
      }
      simulation.reinit();
    }

    return this.reporter;
  }
}

class Simulation {
  id = 0;
  private readonly users: SNSUser[];
  private readonly sources: NewsSource[];
  private readonly model: InputModel;
  private readonly reporter: ReporterStore;
  private readonly nextId: () => number;

  constructor(users: SNSUser[], sources: NewsSource[], model: InputModel, reporter: ReporterStore, nextId: () => number) {
    this.users = users;
    this.sources = sources;
    this.model = model;
    this.reporter = reporter;
    this.nextId = nextId;
    this.reinit();
    log("INFO", `Simulation: created with ${users.length} snsUsers and ${sources.length} newsSources`);
  }

  reinit(): void {
    this.id = this.nextId();
    this.sources.forEach((source) => source.reinit());
    this.users.forEach((user) => user.reinit());
    this.users.forEach((user) => user.setFriends(this.users, this.model.config));
    this.users.forEach((user) => user.setKnownNewsSources(this.filterReach()));
    this.users.forEach((user) => user.setInitialEndorsements(this.model.config));
  }

  step(period: number): void {
    for (const source of this.sources) source.doStep(period);
    for (const user of this.users) {
      user.doStep(period, this.sources, this.model.config, this.reporter, this.id);
    }
    log("DEBUG", `Simulation: Period ${period}`);
    this.applyScenario(period);
    this.report(period);
    if (this.model.config.wom) {
      for (const user of this.users) user.receiveRecommendation(period, this.sources, this.model.config);
    }
  }

  toString(): string {
    return `Simulation{ID=${this.id}, periods=${this.model.config.periods}, snsUsers=${this.users.length}, newsSources=${this.sources.length}}`;
  }

  private filterReach(): NewsSource[] {
    if (!this.model.config.sourceReach) return this.sources;
    return this.sources.filter((source) => Math.random() < source.reach);
  }

  private report(period: number): void {
    this.reporter.addFakeNews(this.model.config, this.id, period,
      this.sources.map((source) => source.isFakeNews(period) ? 1 : 0));
    if (period <= this.model.config.learningPeriods) return;
    const reposts = Array(this.sources.length).fill(0);
    const unique = Array(this.sources.length).fill(0);
    for (const user of this.users) {
      const selected = user.getSelectedNewsSource(period);
      if (!selected) continue;
      selected.addSnsUser(user.id);
      reposts[selected.id] += 1;
    }
    for (const source of this.sources) unique[source.id] = source.uniqueReposters;
    this.reporter.addReposts(this.model.config, this.id, period, reposts, unique);
  }

  private applyScenario(period: number): void {
    const scenario = this.model.scenario;
    if (!scenario || this.model.config.scenario === DISABLED_SCENARIO || period !== scenario.start) return;
    const from = this.sources.find((source) => source.name === scenario.from);
    const to = this.sources.find((source) => source.name === scenario.to);
    if (!from || !to) throw new Error(`Scenario source not found: ${scenario.from} -> ${scenario.to}`);
    log("INFO", `ScenarioManager: Applying Scenario ${this.model.config.scenario} [from=${scenario.from}, to=${scenario.to}]`);
    for (const attr of scenario.attributes) to.replaceAttribute(attr, from.valuesFor(attr));
  }
}

class NewsSource {
  readonly id: number;
  readonly name: string;
  readonly reach: number;
  private readonly baseAttributes: Map<string, number[]>;
  private attributes: Map<string, number[]>;
  private uniqueUsers = new Set<number>();
  private fakeNewsByPeriod = new Map<number, boolean>();

  constructor(id: number, inner: InnerNewsSource) {
    this.id = id;
    this.name = inner.name;
    this.reach = inner.reach;
    this.baseAttributes = new Map(inner.attributeNames.map((name, index) => [name, [...inner.attributeValues[index]]]));
    this.attributes = new Map();
    this.reinit();
    log("INFO", `NewsSource: id=${id}, name='${this.name}', reach='${this.reach}'`);
  }

  get attributeNames(): string[] {
    return [...this.attributes.keys()];
  }

  get uniqueReposters(): number {
    return this.uniqueUsers.size;
  }

  reinit(): void {
    this.attributes = new Map([...this.baseAttributes.entries()].map(([name, values]) => [name, [...values]]));
    this.uniqueUsers.clear();
    this.fakeNewsByPeriod.clear();
  }

  doStep(period: number): void {
    const credibility = this.valuesFor("CREDIBILIDAD DE LA FUENTE");
    this.fakeNewsByPeriod.set(period, credibility[0] > Math.random());
  }

  isFakeNews(period: number): boolean {
    const fakeNews = this.fakeNewsByPeriod.get(period);
    if (fakeNews === undefined) {
      throw new Error(`NewsSource ${this.name} has no fake-news state for period ${period}; doStep(period) must run before this query`);
    }
    return fakeNews;
  }

  valuesFor(attribute: string): number[] {
    const values = this.attributes.get(attribute);
    if (!values) throw new Error(`Attributes: ${attribute} not found`);
    return values;
  }

  replaceAttribute(attribute: string, values: number[]): void {
    if (!this.attributes.has(attribute)) throw new Error(`Attributes: ${attribute} not found`);
    this.attributes.set(attribute, [...values]);
  }

  addSnsUser(id: number): void {
    this.uniqueUsers.add(id);
  }
}

class SNSUser {
  readonly id: number;
  private readonly attributes: Map<string, number>;
  private friends: SNSUser[] = [];
  private knownNewsSources: NewsSource[] = [];
  private endorsements: Endorsement[] = [];
  private currentEvaluation = Number.NEGATIVE_INFINITY;

  constructor(id: number, inner: InnerSNSUser) {
    this.id = id;
    this.attributes = new Map(inner.attributeNames.map((name, index) => [name, inner.attributeValues[index]]));
    log("INFO", `SNSUser: ID=${id}, attributes=${inner.attributeNames.length}`);
  }

  reinit(): void {
    this.friends = [];
    this.knownNewsSources = [];
    this.endorsements = [];
    this.currentEvaluation = Number.NEGATIVE_INFINITY;
  }

  setFriends(users: SNSUser[], config: Config): void {
    const friendSize = Math.min(Math.trunc(config.contacts * config.friends), Math.max(0, users.length - 1));
    while (this.friends.length < friendSize) {
      const candidate = users[Math.trunc(Math.random() * users.length)];
      if (candidate !== this && !this.friends.includes(candidate)) this.friends.push(candidate);
    }
  }

  setKnownNewsSources(sources: NewsSource[]): void {
    this.knownNewsSources = [...sources];
  }

  setInitialEndorsements(config: Config): void {
    for (const source of this.knownNewsSources) {
      this.endorsements.push(...createEndorsements(-1, this, source, config, byMaxLevel));
    }
  }

  doStep(period: number, sources: NewsSource[], config: Config, reporter: ReporterStore, simulationId: number): void {
    if (this.knownNewsSources.length === 0) return;
    const selected = this.selectNewsSource(period, config, reporter, simulationId);
    this.endorsements.push(...createEndorsements(period, this, selected, config, byProbabilityLevel));
    reporter.addAgentDecision(config, simulationId, period, this.id, selected.name, this.currentEvaluation);
    reporter.addEndorsements(config, this.getEndorsementData(simulationId, period));
  }

  receiveRecommendation(period: number, allSources: NewsSource[], config: Config): void {
    const evaluations = new Map<number, number>();
    for (const friend of this.friends) {
      const selected = friend.getSelectedNewsSource(period);
      if (selected) {
        const strongestEvaluation = Math.max(
          evaluations.get(selected.id) ?? Number.NEGATIVE_INFINITY,
          friend.currentEvaluation,
        );
        evaluations.set(selected.id, strongestEvaluation);
      }
    }
    if (evaluations.size === 0) return;
    const selectedId = selectByMax(evaluations);
    let recommended = this.knownNewsSources.find((source) => source.id === selectedId);
    if (!recommended) {
      recommended = allSources.find((source) => source.id === selectedId);
      if (!recommended) return;
      this.knownNewsSources.push(recommended);
    }
    const sign = recommended.isFakeNews(period) ? -1 : 1;
    const mean = ((this.attributes.get("WORD OF MOUTH") ?? 0) / 2) * sign;
    this.endorsements.push({ period: period + 1, newsSource: recommended, attributeName: "WORD OF MOUTH", value: mean });
    void config;
  }

  getWeight(attribute: string): number {
    return this.attributes.get(attribute) ?? 0;
  }

  getSelectedNewsSource(period: number): NewsSource | null {
    const endorsement = this.endorsements.find((item) => item.period === period && item.attributeName !== "WORD OF MOUTH");
    return endorsement?.newsSource ?? null;
  }

  private selectNewsSource(period: number, config: Config, reporter: ReporterStore, simulationId: number): NewsSource {
    const evaluations = new Map<number, number>();
    for (const source of this.knownNewsSources) {
      const values = this.endorsements
        .filter((endorsement) => endorsement.newsSource.name === source.name)
        .filter((endorsement) => config.memory === -1 || endorsement.period > period - config.memory)
        .map((endorsement) => endorsement.value);
      const evaluation = values.reduce((acc, value) => acc + (value > 0 ? Math.pow(config.base, value) : -Math.pow(config.base, Math.abs(value))), 0);
      evaluations.set(source.id, evaluation);
      reporter.addDetailedAgentDecision(config, simulationId, period, this.id, source.name, evaluation);
    }
    const selectedId = selectByProbability(evaluations);
    const selected = this.knownNewsSources.find((source) => source.id === selectedId);
    if (!selected) throw new Error(`Interaction: No NewsSource selected. newsSourceSize:${this.knownNewsSources.length}`);
    this.currentEvaluation = evaluations.get(selectedId) ?? 0;
    return selected;
  }

  private getEndorsementData(simulationId: number, period: number): EndorsementData[] {
    return this.endorsements
      .filter((endorsement) => endorsement.period === period)
      .map((endorsement) => ({
        simulationId,
        period,
        snsUserId: this.id,
        newsSourceName: endorsement.newsSource.name,
        attribute: endorsement.attributeName,
        value: endorsement.value
      }));
  }
}

class ReporterStore {
  agentDecisionData: AgentDecisionData[] = [];
  detailedAgentDecisionData: AgentDecisionData[] = [];
  endorsementData: EndorsementData[] = [];
  repostsPerSourceData: RepostsData[] = [];
  uniqueRepostersPerSourceData: RepostsData[] = [];
  fakeNewsPerSourceData: FakeNewsData[] = [];

  addAgentDecision(config: Config, simulationId: number, period: number, snsUserId: number, newsSourceName: string, evaluation: number): void {
    if (config.savedAgentDecisions) this.agentDecisionData.push({ simulationId, period, snsUserId, newsSourceName, evaluation });
  }

  addDetailedAgentDecision(config: Config, simulationId: number, period: number, snsUserId: number, newsSourceName: string, evaluation: number): void {
    if (config.savedDetailedAgentDecisions) this.detailedAgentDecisionData.push({ simulationId, period, snsUserId, newsSourceName, evaluation });
  }

  addEndorsements(config: Config, endorsements: EndorsementData[]): void {
    if (config.savedEndorsements) this.endorsementData.push(...endorsements);
  }

  addReposts(config: Config, simulationId: number, period: number, reposts: number[], unique: number[]): void {
    if (!config.savedRepostsPerSource) return;
    this.repostsPerSourceData.push({ simulationId, period, reposts: [...reposts] });
    this.uniqueRepostersPerSourceData.push({ simulationId, period, reposts: [...unique] });
  }

  addFakeNews(config: Config, simulationId: number, period: number, fakeNews: number[]): void {
    if (!config.savedFakeNews) return;
    this.fakeNewsPerSourceData.push({ simulationId, period, fakeNews: [...fakeNews] });
  }
}

function createEndorsements(
  period: number,
  user: SNSUser,
  source: NewsSource,
  config: Config,
  strategy: (attributes: number[], mean: number, config: Config) => number
): Endorsement[] {
  return source.attributeNames.map((attributeName) => ({
    period,
    newsSource: source,
    attributeName,
    value: strategy(source.valuesFor(attributeName), user.getWeight(attributeName), config)
  }));
}

function byMaxLevel(attributes: number[], mean: number, config: Config): number {
  let index = 0;
  for (let i = 1; i < config.levels; i += 1) {
    if ((attributes[i] ?? 0) > (attributes[index] ?? 0)) index = i;
  }
  return calculateEndorsementFormula(index + 1, mean, config.levels);
}

function byProbabilityLevel(attributes: number[], mean: number, config: Config): number {
  const random = Math.random();
  let acc = 0;
  let index = attributes.length - 1;
  for (let i = 0; i < config.levels; i += 1) {
    acc += attributes[i] ?? 0;
    if (acc >= random) {
      index = i;
      break;
    }
  }
  return calculateEndorsementFormula(index + 1, mean, config.levels);
}

function calculateEndorsementFormula(index: number, mean: number, levels: number): number {
  let k = Math.floor(index - levels / 2);
  k = levels % 2 === 0 && k <= 0 ? k - 1 : k;
  const div = levels % 2 === 0 ? levels : levels - 1;
  if (k > 0) return mean * k * (2 / div);
  if (k < 0) return mean * k * (1 / div);
  return 0;
}

function selectByMax(evaluations: Map<number, number>): number {
  let selected = -1;
  let max = Number.NEGATIVE_INFINITY;
  for (const [id, value] of evaluations) {
    if (max < value) {
      max = value;
      selected = id;
    }
  }
  if (selected === -1) throw new Error("NewsSourceSelectionStrategies.BY_MAX: no newsSource selected");
  return selected;
}

function selectByProbability(evaluations: Map<number, number>): number {
  const entries = [...evaluations.entries()];
  const min = Math.min(...entries.map(([, value]) => value));
  const weights = entries.map(([id, value]) => [id, min <= 0 ? value - min + 0.0001 : value] as const);
  const sum = weights.reduce((acc, [, value]) => acc + value, 0);
  if (!Number.isFinite(sum) || sum <= 0) return selectByMax(evaluations);
  const random = Math.random();
  let acc = 0;
  for (const [id, value] of weights) {
    acc += value / sum;
    if (acc >= random) return id;
  }
  return weights.length > 0 ? weights[weights.length - 1][0] : selectByMax(evaluations);
}

async function createOutputs(model: InputModel, reporter: ReporterStore, elapsedMs: number): Promise<SimulationOutput> {
  log("INFO", "Reporter: Adding sheets");
  const workbook = XLSX.utils.book_new();
  const csvExports: CsvExport[] = [];
  addAoASheet(workbook, "Configuration", configRows(model.config));
  for (const name of ["NewsSources", "SNSUsers", "SourceReach", "Scenario"]) {
    if (model.sheets[name]) addAoASheet(workbook, name, model.sheets[name]);
  }
  const reportTables = [
    { sheetName: "RepostsPerSource", fileName: "reposts-per-source.csv", rows: repostRows(model.newsSources, reporter.repostsPerSourceData) },
    { sheetName: "UniqueRepostersPerSource", fileName: "unique-reposters-per-source.csv", rows: repostRows(model.newsSources, reporter.uniqueRepostersPerSourceData) },
    { sheetName: "Results", fileName: "results.csv", rows: decisionRows(reporter.agentDecisionData) },
    { sheetName: "DetailedResult", fileName: "detailed-result.csv", rows: decisionRows(reporter.detailedAgentDecisionData) },
    { sheetName: "Endorsements", fileName: "endorsements.csv", rows: endorsementRows(reporter.endorsementData) }
  ];
  if (model.config.savedFakeNews) {
    reportTables.push({
      sheetName: "FakeNewsPerSource",
      fileName: "fake-news-per-source.csv",
      rows: fakeNewsRows(model.newsSources, reporter.fakeNewsPerSourceData)
    });
  }
  for (const table of reportTables) addReportSheet(workbook, csvExports, table.sheetName, table.fileName, table.rows);
  addAoASheet(workbook, "ScenarioChanges", scenarioRows(model));
  if (csvExports.length > 0) {
    addAoASheet(workbook, "ExportedCsvFiles", csvExportIndexRows(csvExports));
  }

  log("INFO", "Reporter: Writing browser artifacts");
  let workbookArray: ArrayBuffer;
  try {
    workbookArray = XLSX.write(workbook, { bookType: "xlsx", type: "array", compression: true }) as ArrayBuffer;
  } catch (error) {
    log("WARN", `Reporter: Workbook was too large for browser XLSX generation. Falling back to CSV report files. ${formatError(error)}`);
    for (const table of reportTables) addCsvExport(csvExports, table.sheetName, table.fileName, table.rows);
    const fallbackWorkbook = XLSX.utils.book_new();
    addAoASheet(fallbackWorkbook, "Configuration", configRows(model.config));
    addAoASheet(fallbackWorkbook, "ExportedCsvFiles", csvExportIndexRows(csvExports));
    workbookArray = XLSX.write(fallbackWorkbook, { bookType: "xlsx", type: "array", compression: true }) as ArrayBuffer;
  }
  const workbookBlob = new Blob([workbookArray], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
  const imageBlobs = await createChartImages(model, reporter);
  const imageUrls = imageBlobs.map((blob) => URL.createObjectURL(blob));
  const zip = new JSZip();
  const stamp = timestamp();
  const outputFolder = `${model.config.fileName}_${stamp}`;
  zip.file(`${outputFolder}/${model.config.fileName}_${stamp}.xlsx`, workbookBlob);
  zip.file(`${outputFolder}/simulation.log`, logLines.join("\n"));
  for (const csvExport of csvExports) {
    zip.file(`${outputFolder}/${csvExport.fileName}`, rowsToCsv(csvExport.rows));
  }
  imageBlobs.forEach((blob, index) => zip.file(`${outputFolder}/chart-${index + 1}.png`, blob));
  const zipBlob = await zip.generateAsync({ type: "blob" });
  const zipUrl = URL.createObjectURL(zipBlob);
  const workbookUrl = URL.createObjectURL(workbookBlob);
  log("INFO", `Reporter: Folder compressed in browser memory: ${outputFolder}.zip`);
  return { workbookBlob, zipBlob, imageUrls, zipUrl, workbookUrl, elapsedMs };
}

function addAoASheet(workbook: XLSX.WorkBook, name: string, rows: CellValue[][]): void {
  XLSX.utils.book_append_sheet(workbook, XLSX.utils.aoa_to_sheet(rows), name.slice(0, 31));
}

function addReportSheet(workbook: XLSX.WorkBook, csvExports: CsvExport[], sheetName: string, fileName: string, rows: CellValue[][]): void {
  if (rows.length <= MAX_XLSX_ROWS) {
    addAoASheet(workbook, sheetName, rows);
    return;
  }
  addCsvExport(csvExports, sheetName, fileName, rows);
  log("WARN", `Reporter: ${sheetName} has ${rows.length - 1} data rows; exporting it as ${fileName} inside the ZIP.`);
}

function addCsvExport(csvExports: CsvExport[], sheetName: string, fileName: string, rows: CellValue[][]): void {
  if (!csvExports.some((item) => item.fileName === fileName)) {
    csvExports.push({ sheetName, fileName, rows });
  }
}

function csvExportIndexRows(exports: CsvExport[]): CellValue[][] {
  return [
    ["SHEET", "ROWS", "CSV_FILE", "NOTE"],
    ...exports.map((item) => [
      item.sheetName,
      Math.max(0, item.rows.length - 1),
      item.fileName,
      `Exported as CSV because the browser workbook limit is ${MAX_XLSX_ROWS} rows per generated sheet.`
    ])
  ];
}

function rowsToCsv(rows: CellValue[][]): string {
  return rows.map((row) => row.map(csvCell).join(",")).join("\n");
}

function csvCell(value: CellValue): string {
  if (value == null) return "";
  const textValue = String(value);
  return /[",\n\r]/.test(textValue) ? `"${textValue.replace(/"/g, '""')}"` : textValue;
}

function configRows(config: Config): CellValue[][] {
  return [
    ["PERIODS", config.periods],
    ["AGENTS", config.agents],
    ["CONTACTS", config.contacts],
    ["FRIENDS", config.friends],
    ["LEVELS", config.levels],
    ["REPETITIONS", config.repetitions],
    ["GUI", config.gui ? 1 : 0],
    ["BASE", config.base],
    ["MEMORY", config.memory],
    ["SOURCE_REACH", config.sourceReach ? 1 : 0],
    ["WOM", config.wom ? 1 : 0],
    ["SCENARIO", config.scenario],
    ["LEARNING_PERIODS", config.learningPeriods],
    ["COMPRESSED_RESULTS", config.compressedResults ? 1 : 0],
    ["SAVED_ENDORSEMENTS", config.savedEndorsements ? 1 : 0],
    ["SAVED_DETAILED_AGENT_DECISIONS", config.savedDetailedAgentDecisions ? 1 : 0],
    ["SAVED_AGENT_DECISIONS", config.savedAgentDecisions ? 1 : 0],
    ["SAVED_REPOSTS_PER_SOURCE", config.savedRepostsPerSource ? 1 : 0],
    ["SAVED_FAKENEWS", config.savedFakeNews ? 1 : 0]
  ];
}

function repostRows(sources: InnerNewsSource[], rows: RepostsData[]): CellValue[][] {
  return [["SIMULATION_ID", "PERIOD", ...sources.map((source) => source.name)], ...rows.map((row) => [row.simulationId, row.period, ...row.reposts])];
}

function fakeNewsRows(sources: InnerNewsSource[], rows: FakeNewsData[]): CellValue[][] {
  return [["Simulation", "Period", ...sources.map((source) => source.name)],
    ...rows.map((row) => [row.simulationId, row.period, ...row.fakeNews])];
}

function decisionRows(rows: AgentDecisionData[]): CellValue[][] {
  return [
    ["SIMULATION_ID", "PERIOD", "SNSUSER_ID", "NEWS_SOURCE_NAME", "EVALUATION"],
    ...rows.map((row) => [row.simulationId, row.period, row.snsUserId, row.newsSourceName, row.evaluation])
  ];
}

function endorsementRows(rows: EndorsementData[]): CellValue[][] {
  return [
    ["SIMULATION_ID", "PERIOD", "SNSUSER_ID", "NEWS_SOURCE_NAME", "ATTRIBUTE", "VALUE"],
    ...rows.map((row) => [row.simulationId, row.period, row.snsUserId, row.newsSourceName, row.attribute, row.value])
  ];
}

function scenarioRows(model: InputModel): CellValue[][] {
  if (!model.scenario) return [];
  return [["FROM", "TO", "START_PERIOD", ...model.scenario.attributes], [model.scenario.from, model.scenario.to, model.scenario.start]];
}

async function createChartImages(model: InputModel, reporter: ReporterStore): Promise<Blob[]> {
  const charts = [
    { title: "Reposts per Source", rows: reporter.repostsPerSourceData },
    { title: "Unique Reposters per Source", rows: reporter.uniqueRepostersPerSourceData }
  ];
  const result: Blob[] = [];
  for (const chart of charts) {
    result.push(await renderLineChart(chart.title, model.newsSources.map((source) => source.name), chart.rows));
  }
  return result;
}

function renderLineChart(title: string, labels: string[], rows: RepostsData[]): Promise<Blob> {
  const canvas = document.createElement("canvas");
  canvas.width = 1200;
  canvas.height = 720;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Canvas context unavailable");
  ctx.fillStyle = "#fbfaf7";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = "#1d2528";
  ctx.font = "700 34px Inter, system-ui, sans-serif";
  ctx.fillText(title, 56, 72);

  const left = 84;
  const top = 112;
  const width = 1040;
  const height = 470;
  const maxValue = rows.reduce((max, row) => Math.max(max, ...row.reposts), 1);
  const periods = [...new Set(rows.map((row) => row.period))].sort((a, b) => a - b);
  const colors = ["#0f766e", "#b42318", "#7c3aed", "#b7791f", "#2563eb", "#db2777"];

  ctx.strokeStyle = "#d6d1c8";
  ctx.lineWidth = 1;
  for (let i = 0; i <= 5; i += 1) {
    const y = top + height - (i / 5) * height;
    ctx.beginPath();
    ctx.moveTo(left, y);
    ctx.lineTo(left + width, y);
    ctx.stroke();
    ctx.fillStyle = "#647074";
    ctx.font = "14px Inter, system-ui, sans-serif";
    ctx.fillText(String(Math.round((i / 5) * maxValue)), 24, y + 5);
  }

  labels.forEach((label, sourceIndex) => {
    ctx.strokeStyle = colors[sourceIndex % colors.length];
    ctx.lineWidth = 3;
    ctx.beginPath();
    rows.forEach((row, rowIndex) => {
      const periodIndex = Math.max(0, periods.indexOf(row.period));
      const x = left + (periodIndex / Math.max(1, periods.length - 1)) * width;
      const y = top + height - ((row.reposts[sourceIndex] ?? 0) / maxValue) * height;
      if (rowIndex === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();
    ctx.fillStyle = colors[sourceIndex % colors.length];
    ctx.fillRect(left + sourceIndex * 240, 632, 20, 12);
    ctx.fillStyle = "#243034";
    ctx.font = "16px Inter, system-ui, sans-serif";
    ctx.fillText(label, left + sourceIndex * 240 + 30, 644);
  });

  return new Promise((resolve, reject) => canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("Image rendering failed"))), "image/png"));
}

function updateLoadedInputUi(model: InputModel | null): void {
  const enabled = Boolean(model);
  els.runButton.disabled = !enabled;
  renderConfigFields(model?.config ?? null);

  if (!model) {
    els.inputName.textContent = "No workbook loaded";
    els.headline.textContent = "Load an Excel input or choose an example.";
    els.sourceCount.textContent = "0";
    els.attributeCount.textContent = "0";
    els.periodCount.textContent = "0";
    els.validation.innerHTML = "";
    return;
  }

  els.inputName.textContent = model.name;
  els.headline.textContent = "Ready to run the simulation.";
  els.sourceCount.textContent = String(model.config.newsSources);
  els.attributeCount.textContent = String(model.config.attributesSource);
  els.periodCount.textContent = String(model.config.periods * (model.config.repetitions + 1));
  els.validation.innerHTML = model.warnings.length
    ? `<strong>Configuration check</strong>${model.warnings.map((warning) => `<p>${escapeHtml(warning)}</p>`).join("")}`
    : "<strong>Configuration check</strong><p>No blocking issues found.</p>";
}

function syncConfigFromControls(): void {
  if (!loadedInput) return;
  for (const field of CONFIG_FIELDS) {
    const input = configInput(field.key);
    if (!input) continue;
    const parsed = parseConfigInput(input.value, field);
    setConfigValue(loadedInput.config, field, parsed);
    input.value = configValueToInput(loadedInput.config, field);
  }
  els.periodCount.textContent = String(loadedInput.config.periods * (loadedInput.config.repetitions + 1));
}

function configInput(key: ConfigEditableKey): HTMLInputElement | null {
  return els.configFields.querySelector<HTMLInputElement>(`input[data-config-key="${key}"]`);
}

function parseConfigInput(value: string, field: ConfigField): number {
  let parsed = Number(value);
  if (!Number.isFinite(parsed)) parsed = field.min ?? 0;
  if (field.integer || field.boolean) parsed = Math.trunc(parsed);
  if (field.boolean) parsed = parsed === 0 ? 0 : 1;
  if (field.min != null) parsed = Math.max(field.min, parsed);
  if (field.max != null) parsed = Math.min(field.max, parsed);
  return parsed;
}

function configValueToInput(config: Config, field: ConfigField): string {
  const value = config[field.key];
  if (typeof value === "boolean") return value ? "1" : "0";
  return String(value);
}

function setConfigValue(config: Config, field: ConfigField, value: number): void {
  if (field.boolean) {
    (config[field.key] as boolean) = value === 1;
    return;
  }
  (config[field.key] as number) = value;
}

function displayOutput(output: SimulationOutput): void {
  els.downloadZip.classList.remove("disabled");
  els.downloadZip.href = output.zipUrl;
  els.downloadZip.download = `${loadedInput?.config.fileName ?? "simulation"}_results.zip`;
  els.downloadWorkbook.classList.remove("disabled");
  els.downloadWorkbook.href = output.workbookUrl;
  els.downloadWorkbook.download = `${loadedInput?.config.fileName ?? "simulation"}_results.xlsx`;
  els.showImages.disabled = false;
  els.preview.innerHTML = output.imageUrls.map((url, index) => `<img src="${url}" alt="Generated chart ${index + 1}" />`).join("");
}

function showImageDialog(): void {
  if (!lastOutput) return;
  els.dialogImages.innerHTML = lastOutput.imageUrls.map((url, index) => `<img src="${url}" alt="Generated chart ${index + 1}" />`).join("");
  els.imageDialog.showModal();
}

function resetResults(): void {
  if (lastOutput) {
    URL.revokeObjectURL(lastOutput.zipUrl);
    URL.revokeObjectURL(lastOutput.workbookUrl);
    lastOutput.imageUrls.forEach((url) => URL.revokeObjectURL(url));
  }
  lastOutput = null;
  els.preview.innerHTML = "";
  els.showImages.disabled = true;
  els.downloadZip.classList.add("disabled");
  els.downloadWorkbook.classList.add("disabled");
  els.downloadZip.removeAttribute("href");
  els.downloadWorkbook.removeAttribute("href");
  updateProgress(0, 100, "Idle");
}

function setBusy(isBusy: boolean, label: string): void {
  els.runButton.disabled = isBusy || !loadedInput;
  els.fileInput.disabled = isBusy;
  els.configFields.querySelectorAll<HTMLInputElement>("input").forEach((input) => {
    input.disabled = isBusy || !loadedInput;
  });
  els.progressLabel.textContent = label;
}

function updateProgress(done: number, total: number, label: string): void {
  const pct = Math.round((done / Math.max(1, total)) * 100);
  els.progress.value = pct;
  els.progressLabel.textContent = label;
  els.progressValue.textContent = `${pct}%`;
}

function log(level: LogLevel, message: string): void {
  const line = `[${new Date().toLocaleTimeString()}] ${level}: ${message}`;
  logLines.push(line);
  els.log.textContent = logLines.join("\n");
  els.log.scrollTop = els.log.scrollHeight;
  refreshLogDownload();
}

function refreshLogDownload(): void {
  if (logDownloadUrl) URL.revokeObjectURL(logDownloadUrl);
  if (logLines.length === 0) {
    logDownloadUrl = null;
    els.downloadLog.classList.add("disabled");
    els.downloadLog.removeAttribute("href");
    return;
  }
  const blob = new Blob([logLines.join("\n")], { type: "text/plain;charset=utf-8" });
  logDownloadUrl = URL.createObjectURL(blob);
  els.downloadLog.href = logDownloadUrl;
  els.downloadLog.download = `simulation-log-${timestamp()}.txt`;
  els.downloadLog.classList.remove("disabled");
}

function text(value: CellValue): string {
  return value == null ? "" : String(value).trim();
}

function num(value: CellValue): number {
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function intConf(conf: Map<string, number>, name: string, fallback: number): number {
  return Math.trunc(conf.get(name) ?? fallback);
}

function numConf(conf: Map<string, number>, name: string, fallback: number): number {
  return conf.get(name) ?? fallback;
}

function boolConf(conf: Map<string, number>, name: string, fallback: boolean): boolean {
  return conf.has(name) ? conf.get(name) === 1 : fallback;
}

function stripExtension(fileName: string): string {
  return fileName.toLowerCase().endsWith(".xlsx") ? fileName.slice(0, -5) : fileName;
}

function formatConfig(config: Config): string {
  return configRows(config).map(([key, value]) => `${key}=${value}`).join(", ");
}

function timestamp(): string {
  const date = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${pad(date.getDate())}-${pad(date.getMonth() + 1)}-${String(date.getFullYear()).slice(2)}(${pad(date.getHours())}-${pad(date.getMinutes())}-${pad(date.getSeconds())})`;
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" })[char] ?? char);
}

function formatError(error: unknown): string {
  if (error instanceof Error) return error.stack ?? error.message;
  return String(error);
}

function nextFrame(): Promise<void> {
  return new Promise((resolve) => requestAnimationFrame(() => resolve()));
}
