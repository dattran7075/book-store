document.addEventListener('DOMContentLoaded', function () {
    // ================== PROFILE FORM ELEMENTS ==================
    const fullName = document.querySelector('#name');
    const email = document.querySelector('#email');
    const address = document.querySelector('#address');
    const phone = document.querySelector('#phone');
    const profileForm = document.querySelector('#form');
    const editBtn = document.querySelector('#edit-btn');
    const saveBtn = document.querySelector('#save-btn');

    // ================== PASSWORD CHANGE ELEMENTS ==================
    const oldPasswordInput = document.querySelector('#oldPassword');
    const newPasswordInput = document.querySelector('#newPassword');
    const confirmPasswordInput = document.querySelector('#confirmNewPassword');
    const confirmPassBtn = document.querySelector('#confirm_pass_btn');
    const editPasswordForm = document.querySelector('#editForm');

    // ================== ERROR DISPLAY FUNCTION ==================
    function showError(input, message) {
        clearError(input);
        const error = document.createElement('div');
        error.className = 'error-message';
        error.style.color = '#d32f2f';
        error.style.fontSize = '13px';
        error.style.marginTop = '6px';
        error.style.marginBottom = '10px';
        error.style.fontWeight = '500';
        error.style.display = 'block';
        error.style.width = '100%';
        error.textContent = message;

        // Find the wrapper div and insert error after it
        const wrapper = input.closest('div[style*="position: relative"]') || input.parentNode;
        wrapper.parentNode.insertBefore(error, wrapper.nextSibling);

        input.style.borderColor = '#d32f2f';
        input.style.borderWidth = '2px';
    }

    function clearError(input) {
        // Find error message after the wrapper
        const wrapper = input.closest('div[style*="position: relative"]') || input.parentNode;
        const error = wrapper.nextElementSibling;
        if (error && error.classList && error.classList.contains('error-message')) {
            error.remove();
        }
        input.style.borderColor = '';
        input.style.borderWidth = '';
    }

    // ================== PROFILE VALIDATORS ==================
    function validateFullName(value) {
        if (!value || !value.trim()) return 'Full name cannot be empty';
        const trimmed = value.trim();
        if (trimmed.length < 2 || trimmed.length > 50)
            return 'Full name must be 2-50 characters';
        if (!/^([A-Z][a-z]+)(\s[A-Z][a-z]+)*$/.test(trimmed))
            return 'Full name must start with capital letter (ex: John Smith)';
        return null;
    }

    function validateEmail(value) {
        if (!value || !value.trim()) return 'Email cannot be empty';
        const regex = /^(?:"(?:[^\x00-\x1F\x22\x5C\x7F-\xFF]|\\[\x20-\x7E])*"|[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]{1,63}(?:\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+){0,31})@(?:(?=.{1,253}$)(?!.*--)(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]{0,61}[\p{L}\p{N}])?\.)+[\p{L}]{2,63})$/u;
        if (!regex.test(value.trim()))
            return 'Invalid email format (ex: user@example.com)';
        return null;
    }

    function validateAddress(value) {
        if (!value || !value.trim()) return 'Address cannot be empty';
        const trimmed = value.trim();
        if (trimmed.length < 5 || trimmed.length > 100)
            return 'Address must be 5-100 characters';
        if (!/^(?!.*[.,#/^()'\-]{2,})(?!-)[A-Za-z0-9\s.,#/^()'\-]{2,50}$/.test(trimmed))
            return 'Address contains invalid characters';
        return null;
    }

    function validatePhone(value) {
        if (!value || !value.trim()) return 'Phone cannot be empty';
        const phoneRegex = /^0[1-9]\d{8}$/;
        if (!phoneRegex.test(value.trim()))
            return 'Phone must start with 0 and have 10 digits (ex: 0123456789)';
        return null;
    }

    // ================== PASSWORD VALIDATORS ==================
    function validateOldPassword(value) {
        if (!value) return 'Old password cannot be empty';
        if (value.length < 6 || value.length > 20)
            return 'Password must be 6-20 characters';
        return null;
    }

    function validateNewPassword(value) {
        if (!value) return 'New password cannot be empty';
        if (value.length < 6 || value.length > 20)
            return 'Password must be 6-20 characters';
        if (!/^(?=.*[A-Za-z])(?=.*\d).+$/.test(value))
            return 'Password must contain at least one letter and one number';
        return null;
    }

    function validateConfirmPassword(newPass, confirmPass) {
        if (!confirmPass) return 'Confirm password cannot be empty';
        if (confirmPass.length < 6 || confirmPass.length > 20)
            return 'Password must be 6-20 characters';
        if (newPass !== confirmPass)
            return 'New password and confirm password do not match';
        return null;
    }

    function validatePasswordsNotSame(oldPass, newPass) {
        if (oldPass === newPass)
            return 'New password must be different from old password';
        return null;
    }

    // ================== VERIFY OLD PASSWORD VIA AJAX ==================
    function verifyOldPasswordAjax(oldPassword) {
        return fetch('/verify-old-password', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                oldPassword: oldPassword
            })
        })
            .then(response => response.json())
            .then(data => {
                return data.isValid;
            })
            .catch(error => {
                console.error('Error verifying password:', error);
                return false;
            });
    }

    // ================== PROFILE FORM - EDIT/SAVE FUNCTIONALITY ==================
    if (editBtn && saveBtn) {
        editBtn.addEventListener('click', (e) => {
            e.preventDefault();
            fullName.removeAttribute('disabled');
            if (email) email.removeAttribute('disabled');
            address.removeAttribute('disabled');
            phone.removeAttribute('disabled');
            saveBtn.style.display = 'inline-block';
            editBtn.style.display = 'none';
        });
    }

    // ================== REAL-TIME ERROR CLEARING ==================
    const profileInputs = [fullName, email, address, phone];
    profileInputs.forEach(input => {
        if (input) {
            input.addEventListener('input', () => clearError(input));
            input.addEventListener('blur', () => {
                if (!input.disabled) {
                    if (input === fullName) {
                        const err = validateFullName(input.value);
                        if (err) showError(input, err);
                    } else if (input === email) {
                        const err = validateEmail(input.value);
                        if (err) showError(input, err);
                    } else if (input === address) {
                        const err = validateAddress(input.value);
                        if (err) showError(input, err);
                    } else if (input === phone) {
                        const err = validatePhone(input.value);
                        if (err) showError(input, err);
                    }
                }
            });
        }
    });

    // ================== PASSWORD CHANGE - REAL-TIME VALIDATION ==================
    if (oldPasswordInput) {
        oldPasswordInput.addEventListener('input', () => {
            clearError(oldPasswordInput);
        });
        oldPasswordInput.addEventListener('blur', () => {
            const err = validateOldPassword(oldPasswordInput.value);
            if (err) showError(oldPasswordInput, err);
        });
    }

    if (newPasswordInput) {
        newPasswordInput.addEventListener('input', () => {
            clearError(newPasswordInput);
            if (confirmPasswordInput && confirmPasswordInput.value) {
                clearError(confirmPasswordInput);
            }
        });
        newPasswordInput.addEventListener('blur', () => {
            const err = validateNewPassword(newPasswordInput.value);
            if (err) showError(newPasswordInput, err);
        });
    }

    if (confirmPasswordInput) {
        confirmPasswordInput.addEventListener('input', () => {
            clearError(confirmPasswordInput);
        });
        confirmPasswordInput.addEventListener('blur', () => {
            if (newPasswordInput) {
                const err = validateConfirmPassword(newPasswordInput.value, confirmPasswordInput.value);
                if (err) showError(confirmPasswordInput, err);
            }
        });
    }

    // ================== PROFILE FORM SUBMISSION ==================
    if (profileForm) {
        profileForm.addEventListener('submit', function (e) {
            let hasError = false;

            const fullNameError = validateFullName(fullName.value);
            if (fullNameError) {
                showError(fullName, fullNameError);
                hasError = true;
            }

            if (email) {
                const emailError = validateEmail(email.value);
                if (emailError) {
                    showError(email, emailError);
                    hasError = true;
                }
            }

            const addressError = validateAddress(address.value);
            if (addressError) {
                showError(address, addressError);
                hasError = true;
            }

            const phoneError = validatePhone(phone.value);
            if (phoneError) {
                showError(phone, phoneError);
                hasError = true;
            }

            if (hasError) {
                e.preventDefault();
                const firstError = profileForm.querySelector('.error-message');
                if (firstError) {
                    firstError.scrollIntoView({behavior: 'smooth', block: 'center'});
                }
            }
        });
    }

    // ================== PASSWORD CHANGE FORM SUBMISSION ==================
    if (editPasswordForm) {
        editPasswordForm.addEventListener('submit', async function (e) {
            e.preventDefault();
            let hasError = false;

            const oldPass = oldPasswordInput.value;
            const newPass = newPasswordInput.value;
            const confirmPass = confirmPasswordInput.value;

            clearError(oldPasswordInput);
            clearError(newPasswordInput);
            clearError(confirmPasswordInput);

            const oldPassError = validateOldPassword(oldPass);
            if (oldPassError) {
                showError(oldPasswordInput, oldPassError);
                hasError = true;
            }

            const newPassError = validateNewPassword(newPass);
            if (newPassError) {
                showError(newPasswordInput, newPassError);
                hasError = true;
            }

            const confirmPassError = validateConfirmPassword(newPass, confirmPass);
            if (confirmPassError) {
                showError(confirmPasswordInput, confirmPassError);
                hasError = true;
            }

            if (!oldPassError && !newPassError) {
                const samePassError = validatePasswordsNotSame(oldPass, newPass);
                if (samePassError) {
                    showError(newPasswordInput, samePassError);
                    hasError = true;
                }
            }

            if (!hasError) {
                try {
                    const isPasswordValid = await verifyOldPasswordAjax(oldPass);
                    if (!isPasswordValid) {
                        showError(oldPasswordInput, 'Old password is incorrect');
                        hasError = true;
                    }
                } catch (error) {
                    console.error('Error during password verification:', error);
                    hasError = true;
                }
            }

            if (hasError) {
                const firstError = editPasswordForm.querySelector('.error-message');
                if (firstError) {
                    firstError.scrollIntoView({behavior: 'smooth', block: 'center'});
                }
            } else {
                editPasswordForm.submit();
            }
        });
    }

    // ================== HANDLE SERVER-SIDE PASSWORD ERRORS ==================
    const messageElement = document.getElementById('message');
    if (messageElement) {
        const serverMessage = messageElement.getAttribute('data-message');

        if (serverMessage && serverMessage !== 'Success') {
            const passwordModal = document.querySelector('#myModal_edit_password');
            if (passwordModal) {
                const modal = new (window.bootstrap ? window.bootstrap.Modal : function () {
                })
                    ? new window.bootstrap.Modal(passwordModal)
                    : null;

                if (modal) modal.show();

                setTimeout(() => {
                    const messageLower = serverMessage.toLowerCase();

                    if (messageLower.includes('old') || messageLower.includes('incorrect')) {
                        showError(oldPasswordInput, serverMessage);
                    } else if (messageLower.includes('match') || messageLower.includes('confirm')) {
                        showError(confirmPasswordInput, serverMessage);
                    } else if (messageLower.includes('new')) {
                        showError(newPasswordInput, serverMessage);
                    } else {
                        showError(oldPasswordInput, serverMessage);
                    }
                }, 100);
            }
        } else if (serverMessage === 'Success') {
            if (window.Swal) {
                Swal.fire({
                    position: 'center',
                    icon: 'success',
                    title: 'Password changed successfully!',
                    showConfirmButton: false,
                    timer: 2000,
                }).then(() => {
                    if (editPasswordForm) {
                        editPasswordForm.reset();
                        clearError(oldPasswordInput);
                        clearError(newPasswordInput);
                        clearError(confirmPasswordInput);
                    }
                });
            }
        }
    }

    // ================== INITIAL STATE ==================
    if (saveBtn) {
        saveBtn.style.display = 'none';
    }
});