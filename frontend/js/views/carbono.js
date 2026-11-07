import { api } from '../api.js';
import {
    emptyState,
    errorPanel,
    escapeHtml,
    formatDateTime,
    loadingPage,
    pageHeader,
    statCard,
} from '../utils.js';
import { notifyDataChanged, setButtonBusy, showToast } from '../ui.js';

const carbonFormatter = new Intl.NumberFormat('es-CO', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 4,
});

function carbonNumber(value) {
    const number = Number(value);
    return Number.isFinite(number) ? carbonFormatter.format(number) : '—';
}

function tableRows(rows, columns, emptyMessage) {
    if (!rows.length) {
        return `<tr><td colspan="${columns.length}">${emptyState({
            icon: 'cloud-slash',
            title: 'Sin registros',
            description: emptyMessage,
        })}</td></tr>`;
    }

    return rows.map((row) => `
        <tr>${columns.map((column) => `<td>${column(row)}</td>`).join('')}</tr>
    `).join('');
}

function render(summary, equipos, selectedCodigo) {
    const equipoOptions = equipos.map((equipo) => `
        <option value="${escapeHtml(equipo.codigo)}" ${equipo.codigo === selectedCodigo ? 'selected' : ''}>
            ${escapeHtml(equipo.codigo)} — ${escapeHtml(equipo.nombre)}
        </option>
    `).join('');
    const hasEquipos = equipos.length > 0;
    const registros = summary.registros || [];
    const areaRows = tableRows(summary.emisionesPorArea || [], [
        (row) => escapeHtml(row.nombreArea),
        (row) => `${carbonNumber(row.emisionesKgCo2)} kg CO₂`,
    ], 'Las emisiones por área aparecerán cuando haya registros.');
    const equipoRows = tableRows(summary.emisionesPorEquipo || [], [
        (row) => escapeHtml(row.codigoEquipo),
        (row) => escapeHtml(row.nombreEquipo),
        (row) => escapeHtml(row.nombreArea),
        (row) => `${carbonNumber(row.emisionesKgCo2)} kg CO₂`,
    ], 'Las emisiones por equipo aparecerán cuando haya registros.');
    const registroRows = tableRows(registros, [
        (row) => escapeHtml(row.codigoEquipo),
        (row) => escapeHtml(row.nombreEquipo),
        (row) => escapeHtml(row.nombreArea),
        (row) => `${carbonNumber(row.horasMalEstado)} h`,
        (row) => `${carbonNumber(row.consumoAdicionalKwh)} kWh`,
        (row) => `${carbonNumber(row.emisionesKgCo2)} kg CO₂`,
        (row) => `${carbonNumber(row.factorEmision)} kg/kWh`,
        (row) => escapeHtml(formatDateTime(row.fechaRegistro)),
    ], 'Aún no se han guardado registros de carbono.');

    return `
        ${pageHeader({
            eyebrow: 'Datos persistidos en PostgreSQL',
            title: 'Huella de carbono',
            subtitle: 'Estimación de emisiones adicionales por equipos en mal estado. No es una medición directa.',
        })}

        <div class="row g-3 mb-3">
            ${statCard({
                label: 'Emisiones adicionales',
                value: `${carbonNumber(summary.totalEmisionesKgCo2)} kg`,
                detail: 'Total histórico de la planta',
                icon: 'cloud-slash',
                tone: 'cyan',
            })}
            ${statCard({
                label: 'Equipos con emisiones',
                value: summary.equiposConEmisiones,
                detail: 'Equipos cuyo acumulado es mayor que cero',
                icon: 'gear',
                tone: 'amber',
            })}
            ${statCard({
                label: 'Registros históricos',
                value: summary.totalRegistros,
                detail: 'Cálculos guardados en PostgreSQL',
                icon: 'database',
                tone: 'blue',
            })}
        </div>

        ${!hasEquipos ? `
            <div class="alert alert-warning d-flex align-items-center gap-2" role="alert">
                <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
                <span>Registre un equipo y asígnele parámetros energéticos antes de calcular emisiones.</span>
                <a class="btn btn-sm btn-outline-secondary ms-auto" href="#/equipos">Ir a equipos</a>
            </div>` : ''}

        <div class="row g-3 mb-3">
            <div class="col-xl-5">
                <section class="panel h-100">
                    <div class="panel-header">
                        <div>
                            <h2 class="panel-title">Registrar emisiones adicionales</h2>
                            <p class="panel-subtitle">Los valores usados y el factor vigente quedarán en el historial.</p>
                        </div>
                    </div>
                    <div class="panel-body">
                        <p class="small text-secondary">
                            Consumo adicional (kWh) = consumo normal (kWh/h) × horas × porcentaje extra / 100.
                            Emisiones (kg CO₂) = consumo adicional × factor (kg CO₂/kWh).
                        </p>
                        <form id="carbono-form">
                            <div class="mb-3">
                                <label class="form-label" for="carbono-equipo">Equipo</label>
                                <select class="form-select" id="carbono-equipo" required ${hasEquipos ? '' : 'disabled'}>
                                    ${hasEquipos ? equipoOptions : '<option value="">No hay equipos disponibles</option>'}
                                </select>
                            </div>
                            <div class="row g-2 mb-3">
                                <div class="col">
                                    <label class="form-label" for="carbono-consumo">Consumo normal (kWh/h)</label>
                                    <input class="form-control" id="carbono-consumo" type="number" min="0" max="99999999.99"
                                           step="0.01" required ${hasEquipos ? '' : 'disabled'}>
                                </div>
                                <div class="col">
                                    <label class="form-label" for="carbono-porcentaje">Porcentaje extra (%)</label>
                                    <input class="form-control" id="carbono-porcentaje" type="number" min="0" max="100"
                                           step="0.01" required ${hasEquipos ? '' : 'disabled'}>
                                </div>
                            </div>
                            <div class="d-flex flex-wrap gap-2 align-items-end mb-3">
                                <div class="flex-grow-1">
                                    <label class="form-label" for="carbono-horas">Horas en mal estado</label>
                                    <input class="form-control" id="carbono-horas" type="number" min="0" max="99999999.99"
                                           step="0.01" required ${hasEquipos ? '' : 'disabled'}>
                                </div>
                                <button class="btn btn-outline-secondary" id="carbono-guardar-parametros" type="button"
                                        ${hasEquipos ? '' : 'disabled'}>Guardar parámetros</button>
                            </div>
                            <button class="btn btn-primary w-100" id="carbono-registrar" type="submit"
                                    ${hasEquipos ? '' : 'disabled'}>Calcular y guardar registro</button>
                        </form>
                    </div>
                </section>
            </div>
            <div class="col-xl-7">
                <section class="panel h-100">
                    <div class="panel-header">
                        <div>
                            <h2 class="panel-title">Factor de emisión de la planta</h2>
                            <p class="panel-subtitle">kg CO₂ por kWh · el factor utilizado se conserva en cada registro.</p>
                        </div>
                    </div>
                    <div class="panel-body">
                        <form id="carbono-factor-form" class="row g-2 align-items-end">
                            <div class="col-sm-8">
                                <label class="form-label" for="carbono-factor">Factor de emisión (kg CO₂/kWh)</label>
                                <input class="form-control" id="carbono-factor" type="number" min="0" max="999999.9999"
                                       step="0.0001" value="${escapeHtml(summary.factorEmision)}" required>
                            </div>
                            <div class="col-sm-4">
                                <button class="btn btn-outline-secondary w-100" id="carbono-guardar-factor" type="submit">
                                    Guardar factor
                                </button>
                            </div>
                        </form>
                    </div>
                </section>
            </div>
        </div>

        <div class="row g-3 mb-3">
            <div class="col-lg-5">
                <section class="panel h-100">
                    <div class="panel-header">
                        <div>
                            <h2 class="panel-title">Emisiones por área</h2>
                            <p class="panel-subtitle">Suma histórica de registros persistidos.</p>
                        </div>
                    </div>
                    <div class="panel-body table-responsive">
                        <table class="table table-hover align-middle">
                            <thead><tr><th>Área</th><th class="text-end">Emisiones</th></tr></thead>
                            <tbody>${areaRows}</tbody>
                        </table>
                    </div>
                </section>
            </div>
            <div class="col-lg-7">
                <section class="panel h-100">
                    <div class="panel-header">
                        <div>
                            <h2 class="panel-title">Emisiones por equipo</h2>
                            <p class="panel-subtitle">Suma histórica por código de equipo.</p>
                        </div>
                    </div>
                    <div class="panel-body table-responsive">
                        <table class="table table-hover align-middle">
                            <thead><tr><th>Código</th><th>Equipo</th><th>Área</th><th class="text-end">Emisiones</th></tr></thead>
                            <tbody>${equipoRows}</tbody>
                        </table>
                    </div>
                </section>
            </div>
        </div>

        <section class="panel">
            <div class="panel-header">
                <div>
                    <h2 class="panel-title">Registros históricos de carbono</h2>
                    <p class="panel-subtitle">Cada fila incluye el factor aplicado al momento del cálculo.</p>
                </div>
            </div>
            <div class="panel-body table-responsive">
                <table class="table table-hover align-middle">
                    <thead>
                        <tr><th>Código</th><th>Equipo</th><th>Área</th><th>Horas</th><th>Consumo adicional</th>
                            <th>Emisiones</th><th>Factor</th><th>Fecha</th></tr>
                    </thead>
                    <tbody>${registroRows}</tbody>
                </table>
            </div>
        </section>`;
}

export async function mount(root) {
    let active = true;
    let selectedCodigo = '';
    let equiposActuales = [];

    async function load() {
        if (!active) return;
        root.innerHTML = loadingPage();
        try {
            const [summary, equipos] = await Promise.all([api.carbono.resumen(), api.equipos.listar()]);
            if (!active) return;
            equiposActuales = equipos;
            if (!equipos.some((equipo) => equipo.codigo === selectedCodigo)) {
                selectedCodigo = equipos[0]?.codigo || '';
            }
            root.innerHTML = render(summary, equipos, selectedCodigo);
            attachEvents();
        } catch (error) {
            if (active) root.innerHTML = errorPanel(error.message);
        }
    }

    function selectedEquipment() {
        return document.getElementById('carbono-equipo')?.value || '';
    }

    function attachEvents() {
        const equipmentSelect = document.getElementById('carbono-equipo');
        const consumoInput = document.getElementById('carbono-consumo');
        const porcentajeInput = document.getElementById('carbono-porcentaje');
        if (equipmentSelect && consumoInput && porcentajeInput) {
            equipmentSelect.addEventListener('change', () => {
                selectedCodigo = selectedEquipment();
                const equipo = equiposActuales.find((item) => item.codigo === selectedCodigo);
                if (equipo) {
                    consumoInput.value = equipo.consumoNormalKwh;
                    porcentajeInput.value = equipo.porcentajeExtra;
                }
            });
            const equipo = equiposActuales.find((item) => item.codigo === selectedCodigo);
            if (equipo) {
                consumoInput.value = equipo.consumoNormalKwh;
                porcentajeInput.value = equipo.porcentajeExtra;
            }
        }

        document.getElementById('carbono-guardar-parametros')?.addEventListener('click', async (event) => {
            if (!consumoInput.reportValidity() || !porcentajeInput.reportValidity()) return;
            const button = event.currentTarget;
            setButtonBusy(button, true, 'Guardando…');
            try {
                const codigo = selectedEquipment();
                await api.carbono.actualizarParametrosEquipo(codigo, {
                    consumoNormalKwh: Number(consumoInput.value),
                    porcentajeExtra: Number(porcentajeInput.value),
                });
                selectedCodigo = codigo;
                showToast('Parámetros energéticos guardados.');
                notifyDataChanged();
                await load();
            } catch (error) {
                showToast(error.message, 'danger');
            } finally {
                setButtonBusy(button, false);
            }
        });

        document.getElementById('carbono-form')?.addEventListener('submit', async (event) => {
            event.preventDefault();
            const button = document.getElementById('carbono-registrar');
            setButtonBusy(button, true, 'Calculando…');
            try {
                const codigoEquipo = selectedEquipment();
                await api.carbono.registrar({
                    codigoEquipo,
                    horasMalEstado: Number(document.getElementById('carbono-horas').value),
                });
                selectedCodigo = codigoEquipo;
                showToast('Cálculo registrado en el historial de carbono.');
                notifyDataChanged();
                await load();
            } catch (error) {
                showToast(error.message, 'danger');
            } finally {
                setButtonBusy(button, false);
            }
        });

        document.getElementById('carbono-factor-form')?.addEventListener('submit', async (event) => {
            event.preventDefault();
            const button = document.getElementById('carbono-guardar-factor');
            setButtonBusy(button, true, 'Guardando…');
            try {
                await api.carbono.actualizarFactor({
                    factorEmision: Number(document.getElementById('carbono-factor').value),
                });
                showToast('Factor de emisión guardado.');
                notifyDataChanged();
                await load();
            } catch (error) {
                showToast(error.message, 'danger');
            } finally {
                setButtonBusy(button, false);
            }
        });
    }

    await load();
    return {
        refresh: load,
        destroy: () => {
            active = false;
        },
    };
}
