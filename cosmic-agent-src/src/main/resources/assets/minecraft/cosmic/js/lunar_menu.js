/* ==========================================================================
   COSMIC CLIENT - 2026 LUNAR / BADLION IN-GAME SHIFT MENU CONTROLLER
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
    const searchInput = document.getElementById('mod-search');
    const catPills = document.querySelectorAll('.cat-pill');
    const modCards = document.querySelectorAll('.mod-card');
    const btnEditHud = document.getElementById('btn-edit-hud-top');
    const btnCloseMenu = document.getElementById('btn-close-menu');
    const hudCanvas = document.getElementById('hud-editor-canvas');
    const btnExitHud = document.getElementById('btn-exit-hud-editor');

    // 1. Search Filter
    if (searchInput) {
        searchInput.addEventListener('input', () => {
            const query = searchInput.value.toLowerCase().trim();
            modCards.forEach(card => {
                const title = card.querySelector('h3')?.textContent.toLowerCase() || '';
                const desc = card.querySelector('p')?.textContent.toLowerCase() || '';
                if (title.includes(query) || desc.includes(query)) {
                    card.style.display = 'flex';
                } else {
                    card.style.display = 'none';
                }
            });
        });
    }

    // 2. Category Filter
    catPills.forEach(pill => {
        pill.addEventListener('click', () => {
            catPills.forEach(p => p.classList.remove('active'));
            pill.classList.add('active');

            const filter = pill.getAttribute('data-filter');
            modCards.forEach(card => {
                const cardCats = card.getAttribute('data-cat') || '';
                if (filter === 'all' || cardCats.includes(filter)) {
                    card.style.display = 'flex';
                } else {
                    card.style.display = 'none';
                }
            });
        });
    });

    // 3. Mod Toggle Persistence
    document.querySelectorAll('.lunar-switch input[type="checkbox"]').forEach(input => {
        // Load saved state
        const saved = localStorage.getItem('cosmic_ingame_' + input.id);
        if (saved !== null) {
            input.checked = saved === 'true';
        }

        input.addEventListener('change', () => {
            localStorage.setItem('cosmic_ingame_' + input.id, input.checked);
        });
    });

    // 4. Edit HUD Mode
    if (btnEditHud && hudCanvas && btnExitHud) {
        btnEditHud.addEventListener('click', () => {
            document.getElementById('lunar-overlay').style.display = 'none';
            hudCanvas.classList.remove('hidden');
        });

        btnExitHud.addEventListener('click', () => {
            hudCanvas.classList.add('hidden');
            document.getElementById('lunar-overlay').style.display = 'flex';
        });
    }

    // 5. ESC / Close Handler
    if (btnCloseMenu) {
        btnCloseMenu.addEventListener('click', () => {
            if (window.cefQuery) {
                window.cefQuery({ request: 'cosmic:close_gui', onSuccess: function(){}, onFailure: function(){} });
            } else {
                document.getElementById('lunar-overlay').style.display = 'none';
            }
        });
    }

    window.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            if (!hudCanvas.classList.contains('hidden')) {
                btnExitHud.click();
            } else if (btnCloseMenu) {
                btnCloseMenu.click();
            }
        }
    });
});
